package ru.labhistory.app.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import ru.labhistory.app.data.AnalysisDocumentEntity;
import ru.labhistory.app.data.AnalysisDao;
import ru.labhistory.app.data.AppDatabase;
import ru.labhistory.app.data.LabResultEntity;
import ru.labhistory.app.data.ProfileEntity;
import ru.labhistory.app.importer.DocumentHasher;

public final class BackupRepository {
    public interface Callback {
        void onSuccess(BackupSummary summary);

        void onError(Exception error);
    }

    private static final int FORMAT_VERSION = 1;
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final long MAX_MANIFEST_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_DOCUMENT_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_TOTAL_DOCUMENT_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_PROFILES = 100;
    private static final int MAX_DOCUMENTS = 10_000;
    private static final int MAX_RESULTS = 200_000;

    private final Context appContext;
    private final AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BackupRepository(Context context) {
        appContext = context.getApplicationContext();
        database = AppDatabase.getInstance(appContext);
    }

    public void exportBackup(Uri destination, char[] password, Callback callback) {
        char[] passwordCopy = copyPassword(password);
        executor.execute(() -> {
            File zipFile = null;
            try {
                zipFile = File.createTempFile("labhistory-export-", ".zip", appContext.getCacheDir());
                BackupSummary summary = buildArchive(zipFile);
                ContentResolver resolver = appContext.getContentResolver();
                try (InputStream input = new FileInputStream(zipFile);
                     OutputStream output = resolver.openOutputStream(destination, "w")) {
                    if (output == null) {
                        throw new IOException("Не удалось открыть выбранное место для записи");
                    }
                    BackupCrypto.encrypt(input, output, passwordCopy);
                }
                callback.onSuccess(summary);
            } catch (Exception error) {
                callback.onError(error);
            } finally {
                Arrays.fill(passwordCopy, '\0');
                deleteFile(zipFile);
            }
        });
    }

    public void importBackup(Uri source, char[] password, Callback callback) {
        char[] passwordCopy = copyPassword(password);
        executor.execute(() -> {
            File zipFile = null;
            File stagingDirectory = null;
            try {
                zipFile = File.createTempFile("labhistory-import-", ".zip", appContext.getCacheDir());
                try (InputStream input = appContext.getContentResolver().openInputStream(source)) {
                    if (input == null) {
                        throw new IOException("Не удалось открыть резервную копию");
                    }
                    BackupCrypto.decrypt(input, zipFile, passwordCopy);
                }
                stagingDirectory = new File(
                        appContext.getCacheDir(),
                        "labhistory-restore-" + UUID.randomUUID()
                );
                if (!stagingDirectory.mkdirs()) {
                    throw new IOException("Не удалось подготовить восстановление");
                }
                List<RestoredProfile> profiles = readArchive(zipFile, stagingDirectory);
                callback.onSuccess(restoreProfiles(profiles));
            } catch (Exception error) {
                callback.onError(error);
            } finally {
                Arrays.fill(passwordCopy, '\0');
                deleteFile(zipFile);
                deleteTree(stagingDirectory);
            }
        });
    }

    private BackupSummary buildArchive(File destination) throws IOException, JSONException {
        List<ProfileEntity> profiles = database.profileDao().getAll();
        JSONArray profileItems = new JSONArray();
        int documentCount = 0;
        int resultCount = 0;

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destination))) {
            for (ProfileEntity profile : profiles) {
                JSONObject profileItem = new JSONObject();
                profileItem.put("name", profile.name);
                putNullable(profileItem, "birthDate", profile.birthDate);
                putNullable(profileItem, "sex", profile.sex);
                profileItem.put("createdAt", profile.createdAt);

                JSONArray documentItems = new JSONArray();
                for (AnalysisDocumentEntity document : database.analysisDao().getDocuments(profile.id)) {
                    JSONObject documentItem = serializeDocument(document);
                    File source = resolveManagedDocument(document.localPath);
                    if (source != null && source.isFile()) {
                        String entryName = "documents/" + UUID.randomUUID()
                                + safeExtension(document.originalFileName, document.sourceType);
                        documentItem.put("fileEntry", entryName);
                        zip.putNextEntry(new ZipEntry(entryName));
                        try (InputStream input = new FileInputStream(source)) {
                            copy(input, zip);
                        }
                        zip.closeEntry();
                    }

                    JSONArray resultItems = new JSONArray();
                    for (LabResultEntity result : database.analysisDao().getResults(document.id)) {
                        resultItems.put(serializeResult(result));
                        resultCount++;
                    }
                    documentItem.put("results", resultItems);
                    documentItems.put(documentItem);
                    documentCount++;
                }
                profileItem.put("documents", documentItems);
                profileItems.put(profileItem);
            }

            JSONObject manifest = new JSONObject();
            manifest.put("formatVersion", FORMAT_VERSION);
            manifest.put("exportedAt", System.currentTimeMillis());
            manifest.put("profiles", profileItems);
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new BackupSummary(profiles.size(), documentCount, resultCount, 0);
    }

    private List<RestoredProfile> readArchive(File source, File stagingDirectory)
            throws IOException, JSONException {
        try (ZipFile zip = new ZipFile(source)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);
            if (manifestEntry == null) {
                throw new IOException("В резервной копии нет описания данных");
            }
            JSONObject manifest;
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                manifest = new JSONObject(readUtf8(input, MAX_MANIFEST_BYTES));
            }
            if (manifest.optInt("formatVersion", -1) != FORMAT_VERSION) {
                throw new IOException("Версия содержимого резервной копии не поддерживается");
            }

            JSONArray profilesJson = manifest.optJSONArray("profiles");
            if (profilesJson == null || profilesJson.length() > MAX_PROFILES) {
                throw new IOException("Некорректное количество профилей в резервной копии");
            }

            List<RestoredProfile> profiles = new ArrayList<>();
            int totalDocuments = 0;
            int totalResults = 0;
            long totalDocumentBytes = 0L;
            for (int profileIndex = 0; profileIndex < profilesJson.length(); profileIndex++) {
                JSONObject profileJson = profilesJson.getJSONObject(profileIndex);
                String profileName = clean(profileJson.optString("name", ""));
                if (profileName.isEmpty()) {
                    profileName = "Восстановленный профиль";
                }
                ProfileEntity profile = new ProfileEntity(
                        profileName,
                        nullableString(profileJson, "birthDate"),
                        nullableString(profileJson, "sex"),
                        profileJson.optLong("createdAt", System.currentTimeMillis())
                );
                RestoredProfile restoredProfile = new RestoredProfile(profile);
                JSONArray documentsJson = profileJson.optJSONArray("documents");
                if (documentsJson == null) {
                    documentsJson = new JSONArray();
                }
                totalDocuments += documentsJson.length();
                if (totalDocuments > MAX_DOCUMENTS) {
                    throw new IOException("Слишком много документов в резервной копии");
                }

                for (int documentIndex = 0; documentIndex < documentsJson.length(); documentIndex++) {
                    JSONObject documentJson = documentsJson.getJSONObject(documentIndex);
                    AnalysisDocumentEntity document = deserializeDocument(documentJson);
                    File stagedFile = null;
                    String fileEntryName = nullableString(documentJson, "fileEntry");
                    if (fileEntryName != null) {
                        validateFileEntry(fileEntryName);
                        ZipEntry fileEntry = zip.getEntry(fileEntryName);
                        if (fileEntry == null || fileEntry.isDirectory()) {
                            throw new IOException("В резервной копии отсутствует документ");
                        }
                        stagedFile = new File(
                                stagingDirectory,
                                UUID.randomUUID() + safeExtension(
                                        document.originalFileName,
                                        document.sourceType
                                )
                        );
                        long extracted;
                        try (InputStream input = zip.getInputStream(fileEntry);
                             OutputStream output = new FileOutputStream(stagedFile)) {
                            extracted = copyLimited(input, output, MAX_DOCUMENT_BYTES);
                        }
                        totalDocumentBytes += extracted;
                        if (totalDocumentBytes > MAX_TOTAL_DOCUMENT_BYTES) {
                            throw new IOException("Документы в резервной копии слишком большие");
                        }
                        String actualHash = DocumentHasher.sha256(stagedFile);
                        if (document.contentHash != null
                                && !document.contentHash.equalsIgnoreCase(actualHash)) {
                            throw new IOException("Контрольная сумма документа не совпадает");
                        }
                        document.contentHash = actualHash;
                    }

                    RestoredDocument restoredDocument = new RestoredDocument(document, stagedFile);
                    JSONArray resultsJson = documentJson.optJSONArray("results");
                    if (resultsJson == null) {
                        resultsJson = new JSONArray();
                    }
                    totalResults += resultsJson.length();
                    if (totalResults > MAX_RESULTS) {
                        throw new IOException("Слишком много показателей в резервной копии");
                    }
                    for (int resultIndex = 0; resultIndex < resultsJson.length(); resultIndex++) {
                        restoredDocument.results.add(deserializeResult(
                                resultsJson.getJSONObject(resultIndex)
                        ));
                    }
                    restoredProfile.documents.add(restoredDocument);
                }
                profiles.add(restoredProfile);
            }
            return profiles;
        }
    }

    private BackupSummary restoreProfiles(List<RestoredProfile> profiles) throws IOException {
        List<File> createdFiles = new ArrayList<>();
        int[] counts = new int[4];
        try {
            database.runInTransaction(() -> {
                try {
                    AnalysisDao dao = database.analysisDao();
                    File documentsDirectory = new File(appContext.getFilesDir(), "analysis_documents");
                    if (!documentsDirectory.exists() && !documentsDirectory.mkdirs()) {
                        throw new IOException("Не удалось создать папку документов");
                    }
                    for (RestoredProfile restoredProfile : profiles) {
                        ProfileEntity existing = database.profileDao().findByName(
                                restoredProfile.profile.name
                        );
                        long profileId;
                        if (existing == null) {
                            profileId = database.profileDao().insert(restoredProfile.profile);
                        } else {
                            profileId = existing.id;
                        }
                        counts[0]++;

                        for (RestoredDocument restoredDocument : restoredProfile.documents) {
                            AnalysisDocumentEntity document = restoredDocument.document;
                            boolean duplicateFile = document.contentHash != null
                                    && dao.findDocumentIdByHash(
                                    profileId,
                                    document.contentHash
                            ) != null;
                            boolean duplicateManual = "MANUAL".equals(document.sourceType)
                                    && dao.findManualDocumentId(
                                    profileId,
                                    document.analysisDate,
                                    document.createdAt
                            ) != null;
                            if (duplicateFile || duplicateManual) {
                                counts[3]++;
                                continue;
                            }

                            File finalFile = null;
                            if (restoredDocument.stagedFile != null) {
                                finalFile = new File(
                                        documentsDirectory,
                                        UUID.randomUUID() + safeExtension(
                                                document.originalFileName,
                                                document.sourceType
                                        )
                                );
                                copyFile(restoredDocument.stagedFile, finalFile);
                                createdFiles.add(finalFile);
                            }
                            document.profileId = profileId;
                            document.localPath = finalFile == null
                                    ? null
                                    : finalFile.getAbsolutePath();
                            long documentId = dao.insertDocument(document);
                            counts[1]++;
                            for (LabResultEntity result : restoredDocument.results) {
                                result.documentId = documentId;
                                dao.insertResult(result);
                                counts[2]++;
                            }
                        }
                    }
                } catch (IOException error) {
                    throw new RestoreRuntimeException(error);
                }
            });
        } catch (RestoreRuntimeException error) {
            for (File createdFile : createdFiles) {
                deleteFile(createdFile);
            }
            throw error.ioException;
        } catch (RuntimeException error) {
            for (File createdFile : createdFiles) {
                deleteFile(createdFile);
            }
            throw error;
        }
        return new BackupSummary(counts[0], counts[1], counts[2], counts[3]);
    }

    private JSONObject serializeDocument(AnalysisDocumentEntity document) throws JSONException {
        JSONObject item = new JSONObject();
        putNullable(item, "labName", document.labName);
        item.put("analysisDate", document.analysisDate);
        putNullable(item, "analysisDateSource", document.analysisDateSource);
        putNullable(item, "sourceType", document.sourceType);
        putNullable(item, "originalFileName", document.originalFileName);
        putNullable(item, "contentHash", document.contentHash);
        putNullable(item, "importStatus", document.importStatus);
        putNullable(item, "ocrText", document.ocrText);
        item.put("createdAt", document.createdAt);
        return item;
    }

    private JSONObject serializeResult(LabResultEntity result) throws JSONException {
        JSONObject item = new JSONObject();
        putNullable(item, "testName", result.testName);
        putNullable(item, "canonicalKey", result.canonicalKey);
        putNullable(item, "canonicalName", result.canonicalName);
        putNullable(item, "rawValue", result.rawValue);
        putNullable(item, "numericValue", result.numericValue);
        putNullable(item, "unit", result.unit);
        putNullable(item, "canonicalUnit", result.canonicalUnit);
        putNullable(item, "referenceLow", result.referenceLow);
        putNullable(item, "referenceHigh", result.referenceHigh);
        putNullable(item, "referenceText", result.referenceText);
        item.put("confidence", result.confidence);
        return item;
    }

    private AnalysisDocumentEntity deserializeDocument(JSONObject item) {
        return new AnalysisDocumentEntity(
                0L,
                nullableString(item, "labName"),
                item.optLong("analysisDate", System.currentTimeMillis()),
                valueOrDefault(nullableString(item, "analysisDateSource"), "IMPORT"),
                valueOrDefault(nullableString(item, "sourceType"), "PDF"),
                valueOrDefault(nullableString(item, "originalFileName"), "document"),
                null,
                nullableString(item, "contentHash"),
                valueOrDefault(nullableString(item, "importStatus"), "Восстановлено из копии"),
                nullableString(item, "ocrText"),
                item.optLong("createdAt", System.currentTimeMillis())
        );
    }

    private LabResultEntity deserializeResult(JSONObject item) {
        return new LabResultEntity(
                0L,
                nullableString(item, "testName"),
                nullableString(item, "canonicalKey"),
                nullableString(item, "canonicalName"),
                nullableString(item, "rawValue"),
                nullableString(item, "numericValue"),
                nullableString(item, "unit"),
                nullableString(item, "canonicalUnit"),
                nullableString(item, "referenceLow"),
                nullableString(item, "referenceHigh"),
                nullableString(item, "referenceText"),
                item.optDouble("confidence", 0.0)
        );
    }

    private File resolveManagedDocument(String localPath) throws IOException {
        if (localPath == null || localPath.trim().isEmpty()) {
            return null;
        }
        File root = new File(appContext.getFilesDir(), "analysis_documents").getCanonicalFile();
        File candidate = new File(localPath).getCanonicalFile();
        String rootPrefix = root.getPath() + File.separator;
        if (!candidate.getPath().startsWith(rootPrefix)) {
            throw new IOException("Документ находится вне внутреннего хранилища приложения");
        }
        return candidate;
    }

    private static char[] copyPassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Пароль должен содержать не менее 8 символов");
        }
        return Arrays.copyOf(password, password.length);
    }

    private static void validateFileEntry(String name) throws IOException {
        if (!name.startsWith("documents/")
                || name.contains("..")
                || name.startsWith("/")
                || name.contains("\\")) {
            throw new IOException("Некорректный путь документа в резервной копии");
        }
    }

    private static String safeExtension(String fileName, String sourceType) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
                if (extension.matches("\\.(pdf|jpe?g|png|webp|heic|heif)")) {
                    return extension;
                }
            }
        }
        return "PDF".equalsIgnoreCase(sourceType) ? ".pdf" : ".jpg";
    }

    private static String readUtf8(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyLimited(input, output, limit);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static long copyLimited(InputStream input, OutputStream output, long limit)
            throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("Файл в резервной копии превышает допустимый размер");
            }
            output.write(buffer, 0, read);
        }
        return total;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copy(input, output);
        } catch (IOException error) {
            deleteFile(destination);
            throw error;
        }
    }

    private static void putNullable(JSONObject object, String name, String value)
            throws JSONException {
        object.put(name, value == null ? JSONObject.NULL : value);
    }

    private static String nullableString(JSONObject object, String name) {
        if (!object.has(name) || object.isNull(name)) {
            return null;
        }
        String value = object.optString(name, null);
        return value == null ? null : value;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static void deleteFile(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static void deleteTree(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteTree(child);
                } else {
                    deleteFile(child);
                }
            }
        }
        deleteFile(directory);
    }

    private static final class RestoredProfile {
        final ProfileEntity profile;
        final List<RestoredDocument> documents = new ArrayList<>();

        RestoredProfile(ProfileEntity profile) {
            this.profile = profile;
        }
    }

    private static final class RestoredDocument {
        final AnalysisDocumentEntity document;
        final File stagedFile;
        final List<LabResultEntity> results = new ArrayList<>();

        RestoredDocument(AnalysisDocumentEntity document, File stagedFile) {
            this.document = document;
            this.stagedFile = stagedFile;
        }
    }

    private static final class RestoreRuntimeException extends RuntimeException {
        final IOException ioException;

        RestoreRuntimeException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}
