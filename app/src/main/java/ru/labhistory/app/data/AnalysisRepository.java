package ru.labhistory.app.data;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.labhistory.app.domain.ManualResultInput;
import ru.labhistory.app.domain.ReviewResultInput;
import ru.labhistory.app.export.CsvProfileExporter;
import ru.labhistory.app.importer.DocumentImporter;
import ru.labhistory.app.importer.DocumentHasher;
import ru.labhistory.app.importer.ImportedDocument;
import ru.labhistory.app.importer.LabFormatDetector;
import ru.labhistory.app.ocr.DocumentRecognitionResult;
import ru.labhistory.app.ocr.LocalOcrEngine;
import ru.labhistory.app.parser.AnalysisDateExtractor;
import ru.labhistory.app.parser.ResultValueParser;
import ru.labhistory.app.parser.TestNameNormalizer;
import ru.labhistory.app.parser.UnitNormalizer;

public class AnalysisRepository {
    public interface Callback<T> {
        void onSuccess(T value);

        void onError(Exception error);
    }

    private final AppDatabase database;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DocumentImporter documentImporter = new DocumentImporter();
    private final LocalOcrEngine ocrEngine;

    public AnalysisRepository(Context context) {
        appContext = context.getApplicationContext();
        database = AppDatabase.getInstance(appContext);
        ocrEngine = new LocalOcrEngine(appContext);
        executor.execute(this::normalizeLegacyResults);
    }

    public LiveData<List<ProfileEntity>> observeProfiles() {
        return database.profileDao().observeAll();
    }

    public LiveData<List<AnalysisDocumentEntity>> observeDocuments(long profileId) {
        return database.analysisDao().observeDocuments(profileId);
    }

    public LiveData<AnalysisDocumentEntity> observeDocument(long documentId) {
        return database.analysisDao().observeDocument(documentId);
    }

    public void ensureDefaultProfile() {
        executor.execute(() -> {
            if (database.profileDao().count() == 0) {
                database.profileDao().insert(new ProfileEntity(
                        "Я",
                        null,
                        null,
                        System.currentTimeMillis()
                ));
            }
        });
    }

    public void addProfile(
            String name,
            String birthDate,
            String sex,
            Callback<ProfileEntity> callback
    ) {
        executor.execute(() -> {
            try {
                String cleanedName = clean(name);
                if (cleanedName.isEmpty()) {
                    throw new IllegalArgumentException("Введите имя или псевдоним");
                }
                if (database.profileDao().findByName(cleanedName) != null) {
                    throw new IllegalArgumentException("Профиль с таким именем уже существует");
                }
                ProfileEntity profile = new ProfileEntity(
                        cleanedName,
                        emptyToNull(birthDate),
                        emptyToNull(sex),
                        System.currentTimeMillis()
                );
                profile.id = database.profileDao().insert(profile);
                callback.onSuccess(profile);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void updateProfile(
            long profileId,
            String name,
            String birthDate,
            String sex,
            Callback<ProfileEntity> callback
    ) {
        executor.execute(() -> {
            try {
                ProfileEntity profile = database.profileDao().get(profileId);
                if (profile == null) {
                    throw new IllegalArgumentException("Профиль не найден");
                }
                String cleanedName = clean(name);
                if (cleanedName.isEmpty()) {
                    throw new IllegalArgumentException("Введите имя или псевдоним");
                }
                ProfileEntity sameName = database.profileDao().findByName(cleanedName);
                if (sameName != null && sameName.id != profileId) {
                    throw new IllegalArgumentException("Профиль с таким именем уже существует");
                }
                profile.name = cleanedName;
                profile.birthDate = emptyToNull(birthDate);
                profile.sex = emptyToNull(sex);
                if (database.profileDao().update(profile) == 0) {
                    throw new IllegalArgumentException("Профиль не найден");
                }
                callback.onSuccess(profile);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void deleteProfile(long profileId, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                if (database.profileDao().count() <= 1) {
                    throw new IllegalArgumentException("Нельзя удалить единственный профиль");
                }
                if (database.profileDao().get(profileId) == null) {
                    throw new IllegalArgumentException("Профиль не найден");
                }
                List<File> managedFiles = new ArrayList<>();
                for (AnalysisDocumentEntity document : database.analysisDao().getDocuments(profileId)) {
                    if (document.localPath == null) {
                        continue;
                    }
                    try {
                        File file = requireManagedDocument(document.localPath);
                        if (file.isFile()) {
                            managedFiles.add(file);
                        }
                    } catch (IOException ignored) {
                        // Никогда не удаляем путь за пределами внутренней папки приложения.
                    }
                }
                if (database.profileDao().delete(profileId) == 0) {
                    throw new IllegalArgumentException("Профиль не найден");
                }
                for (File file : managedFiles) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                callback.onSuccess(null);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void importDocument(
            Context context,
            long profileId,
            Uri uri,
            Callback<AnalysisDocumentEntity> callback
    ) {
        executor.execute(() -> {
            ImportedDocument imported = null;
            boolean persisted = false;
            try {
                imported = documentImporter.copyToPrivateStorage(context, uri);
                Long duplicateId = database.analysisDao().findDocumentIdByHash(
                        profileId,
                        imported.contentHash
                );
                if (duplicateId != null) {
                    deleteUnpersistedCopy(imported.localPath);
                    imported = null;
                    throw new IllegalArgumentException(
                            "Этот файл уже загружен в выбранный профиль"
                    );
                }
                long now = System.currentTimeMillis();
                AnalysisDocumentEntity entity = new AnalysisDocumentEntity(
                        profileId,
                        imported.labName,
                        now,
                        "IMPORT",
                        imported.sourceType,
                        imported.originalFileName,
                        imported.localPath,
                        imported.contentHash,
                        "Сохранён, ожидает обработки",
                        null,
                        now
                );
                entity.id = database.analysisDao().insertDocument(entity);
                persisted = true;
                callback.onSuccess(entity);
            } catch (Exception error) {
                if (!persisted && imported != null) {
                    deleteUnpersistedCopy(imported.localPath);
                }
                callback.onError(error);
            }
        });
    }

    public void addManualResult(
            long profileId,
            ManualResultInput input,
            Callback<Long> callback
    ) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                final long[] documentId = new long[1];
                database.runInTransaction(() -> {
                    AnalysisDocumentEntity document = new AnalysisDocumentEntity(
                            profileId,
                            input.labName.trim().isEmpty() ? "Ручной ввод" : input.labName,
                            now,
                            "MANUAL",
                            "MANUAL",
                            "Ручной ввод",
                            null,
                            null,
                            "Проверено пользователем",
                            null,
                            now
                    );
                    documentId[0] = database.analysisDao().insertDocument(document);

                    LabResultEntity result = createResult(
                            documentId[0],
                            input.testName,
                            input.value,
                            input.unit,
                            input.referenceLow,
                            input.referenceHigh,
                            buildReferenceText(input.referenceLow, input.referenceHigh)
                    );
                    database.analysisDao().insertResult(result);
                });
                callback.onSuccess(documentId[0]);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void loadResults(long documentId, Callback<List<LabResultEntity>> callback) {
        executor.execute(() -> {
            try {
                callback.onSuccess(database.analysisDao().getResults(documentId));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void loadIndicatorSummaries(long profileId, Callback<List<IndicatorSummary>> callback) {
        executor.execute(() -> {
            try {
                callback.onSuccess(database.analysisDao().getIndicatorSummaries(profileId));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void exportProfileCsv(
            long profileId,
            Uri destination,
            Callback<Integer> callback
    ) {
        executor.execute(() -> {
            try {
                ProfileEntity profile = database.profileDao().get(profileId);
                if (profile == null) {
                    throw new IllegalArgumentException("Профиль не найден");
                }
                List<ProfileExportRow> rows = database.analysisDao()
                        .getProfileExportRows(profileId);
                int exported;
                try (OutputStream output = appContext.getContentResolver()
                        .openOutputStream(destination, "w")) {
                    if (output == null) {
                        throw new IOException("Не удалось открыть выбранное место для записи");
                    }
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                            output,
                            StandardCharsets.UTF_8
                    ))) {
                        exported = CsvProfileExporter.write(writer, profile, rows);
                    }
                }
                callback.onSuccess(exported);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void loadIndicatorMeasurements(
            long profileId,
            String canonicalKey,
            String unit,
            Callback<List<IndicatorMeasurement>> callback
    ) {
        executor.execute(() -> {
            try {
                callback.onSuccess(database.analysisDao().getIndicatorMeasurements(
                        profileId,
                        canonicalKey,
                        unit == null ? "" : unit
                ));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void loadDocument(long documentId, Callback<AnalysisDocumentEntity> callback) {
        executor.execute(() -> {
            try {
                AnalysisDocumentEntity document = database.analysisDao().getDocument(documentId);
                if (document == null) {
                    throw new IllegalArgumentException("Документ не найден");
                }
                callback.onSuccess(document);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void renameIndicatorGroup(
            long profileId,
            String canonicalKey,
            String unit,
            String requestedName,
            Callback<TestNameNormalizer.NormalizedTest> callback
    ) {
        executor.execute(() -> {
            try {
                String cleanedName = clean(requestedName);
                if (cleanedName.isEmpty()) {
                    throw new IllegalArgumentException("Введите название показателя");
                }
                TestNameNormalizer.NormalizedTest normalized = TestNameNormalizer.normalize(cleanedName);
                int updated = database.analysisDao().renameIndicatorGroup(
                        profileId,
                        canonicalKey,
                        unit == null ? "" : unit,
                        normalized.key,
                        normalized.displayName
                );
                if (updated == 0) {
                    throw new IllegalArgumentException("Показатель не найден");
                }
                callback.onSuccess(normalized);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void updateIndicatorMeasurement(
            long profileId,
            long resultId,
            String requestedName,
            String requestedValue,
            String requestedUnit,
            String requestedReference,
            Callback<Void> callback
    ) {
        executor.execute(() -> {
            try {
                LabResultEntity result = database.analysisDao().getResultForProfile(
                        profileId,
                        resultId
                );
                if (result == null) {
                    throw new IllegalArgumentException("Измерение не найдено");
                }
                String name = clean(requestedName);
                String value = clean(requestedValue);
                String normalizedNumber = ResultValueParser.normalizeNumber(value);
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Введите название показателя");
                }
                if (normalizedNumber == null) {
                    throw new IllegalArgumentException("Введите одно числовое значение");
                }

                String reference = clean(requestedReference);
                String[] range = ResultValueParser.parseReferenceRange(reference);
                String cleanedUnit = clean(requestedUnit);
                TestNameNormalizer.NormalizedTest normalizedTest = TestNameNormalizer.normalize(name);
                result.testName = name;
                result.canonicalKey = normalizedTest.key;
                result.canonicalName = normalizedTest.displayName;
                result.rawValue = value;
                result.numericValue = normalizedNumber;
                result.unit = cleanedUnit;
                result.canonicalUnit = UnitNormalizer.normalize(cleanedUnit);
                result.referenceLow = range[0];
                result.referenceHigh = range[1];
                result.referenceText = reference;
                result.confidence = 1.0;
                database.analysisDao().updateResult(result);
                callback.onSuccess(null);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void deleteIndicatorMeasurement(
            long profileId,
            long resultId,
            Callback<Void> callback
    ) {
        executor.execute(() -> {
            try {
                if (database.analysisDao().deleteResultForProfile(profileId, resultId) == 0) {
                    throw new IllegalArgumentException("Измерение уже удалено или не найдено");
                }
                callback.onSuccess(null);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void recognizeDocument(long documentId, Callback<AnalysisDocumentEntity> callback) {
        executor.execute(() -> {
            AnalysisDocumentEntity document = null;
            try {
                document = database.analysisDao().getDocument(documentId);
                if (document == null) {
                    throw new IllegalArgumentException("Документ не найден");
                }
                if (document.localPath == null || "MANUAL".equals(document.sourceType)) {
                    throw new IllegalArgumentException("У этого документа нет файла для обработки");
                }

                File source = requireManagedDocument(document.localPath);
                if (!source.isFile()) {
                    throw new IOException("Исходный файл не найден");
                }

                database.analysisDao().updateStatus(documentId, "Обработка на устройстве…");
                DocumentRecognitionResult recognition = ocrEngine.recognize(source, document.sourceType);
                String recognizedText = recognition.text.trim();
                if (recognizedText.isEmpty()) {
                    throw new IOException("Текст не найден. Попробуйте более чёткое фото");
                }

                String detectedLab = LabFormatDetector.detect(recognizedText);
                String labName = "Не определена".equals(detectedLab)
                        ? document.labName
                        : detectedLab;
                database.analysisDao().updateOcr(
                        documentId,
                        recognizedText,
                        labName,
                        "Данные извлечены, проверьте их"
                );
                if (recognition.detectedAnalysisDate != null) {
                    database.analysisDao().updateDetectedAnalysisDate(
                            documentId,
                            recognition.detectedAnalysisDate
                    );
                }
                callback.onSuccess(database.analysisDao().getDocument(documentId));
            } catch (Exception error) {
                if (document != null) {
                    database.analysisDao().updateStatus(documentId, "Ошибка обработки");
                }
                callback.onError(error);
            }
        });
    }

    public void saveReviewedText(long documentId, String ocrText, Callback<AnalysisDocumentEntity> callback) {
        executor.execute(() -> {
            try {
                AnalysisDocumentEntity document = database.analysisDao().getDocument(documentId);
                if (document == null) {
                    throw new IllegalArgumentException("Документ не найден");
                }
                String normalizedText = ocrText == null ? "" : ocrText.trim();
                if (normalizedText.isEmpty()) {
                    throw new IllegalArgumentException("Извлечённый текст не может быть пустым");
                }
                String detectedLab = LabFormatDetector.detect(normalizedText);
                String labName = "Не определена".equals(detectedLab)
                        ? document.labName
                        : detectedLab;
                database.analysisDao().updateOcr(
                        documentId,
                        normalizedText,
                        labName,
                        "Текст проверен пользователем"
                );
                Long detectedDate = AnalysisDateExtractor.extract(normalizedText);
                if (detectedDate != null) {
                    database.analysisDao().updateDetectedAnalysisDate(documentId, detectedDate);
                }
                callback.onSuccess(database.analysisDao().getDocument(documentId));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void saveReviewedResults(
            long documentId,
            List<ReviewResultInput> inputs,
            Callback<Void> callback
    ) {
        executor.execute(() -> {
            try {
                if (inputs == null || inputs.isEmpty()) {
                    throw new IllegalArgumentException("Добавьте хотя бы один показатель");
                }
                AnalysisDocumentEntity document = database.analysisDao().getDocument(documentId);
                if (document == null) {
                    throw new IllegalArgumentException("Документ не найден");
                }

                List<LabResultEntity> results = new ArrayList<>();
                for (ReviewResultInput input : inputs) {
                    String testName = clean(input.testName);
                    String value = clean(input.value);
                    if (testName.isEmpty() || value.isEmpty()) {
                        throw new IllegalArgumentException("У каждого показателя должны быть название и значение");
                    }
                    String referenceText = clean(input.referenceText);
                    String[] range = ResultValueParser.parseReferenceRange(referenceText);
                    results.add(createResult(
                            documentId,
                            testName,
                            value,
                            clean(input.unit),
                            range[0],
                            range[1],
                            referenceText
                    ));
                }

                database.runInTransaction(() -> {
                    database.analysisDao().deleteResults(documentId);
                    for (LabResultEntity result : results) {
                        database.analysisDao().insertResult(result);
                    }
                    database.analysisDao().updateStatus(
                            documentId,
                            "Показатели проверены пользователем"
                    );
                });
                callback.onSuccess(null);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void updateAnalysisDate(
            long documentId,
            long analysisDate,
            Callback<AnalysisDocumentEntity> callback
    ) {
        executor.execute(() -> {
            try {
                AnalysisDocumentEntity document = database.analysisDao().getDocument(documentId);
                if (document == null) {
                    throw new IllegalArgumentException("Документ не найден");
                }
                database.analysisDao().updateAnalysisDateByUser(documentId, analysisDate);
                callback.onSuccess(database.analysisDao().getDocument(documentId));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    public void deleteDocument(long documentId, Callback<Void> callback) {
        executor.execute(() -> {
            File original = null;
            File staged = null;
            try {
                AnalysisDocumentEntity document = database.analysisDao().getDocument(documentId);
                if (document == null) {
                    throw new IllegalArgumentException("Документ уже удалён");
                }

                if (document.localPath != null) {
                    original = requireManagedDocument(document.localPath);
                    if (original.exists()) {
                        staged = new File(original.getParentFile(), original.getName() + ".deleting");
                        if (!original.renameTo(staged)) {
                            throw new IOException("Не удалось подготовить файл к удалению");
                        }
                    }
                }

                if (database.analysisDao().deleteDocument(documentId) == 0) {
                    throw new IOException("Не удалось удалить запись из базы");
                }

                if (staged != null && staged.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    staged.delete();
                }
                callback.onSuccess(null);
            } catch (Exception error) {
                if (staged != null && staged.exists() && original != null) {
                    //noinspection ResultOfMethodCallIgnored
                    staged.renameTo(original);
                }
                callback.onError(error);
            }
        });
    }

    private File requireManagedDocument(String localPath) throws IOException {
        File documentsDirectory = new File(appContext.getFilesDir(), "analysis_documents")
                .getCanonicalFile();
        File candidate = new File(localPath).getCanonicalFile();
        String rootPath = documentsDirectory.getPath() + File.separator;
        if (!candidate.getPath().startsWith(rootPath)) {
            throw new SecurityException("Недопустимый путь к документу");
        }
        return candidate;
    }

    private String buildReferenceText(String low, String high) {
        if (low.trim().isEmpty() && high.trim().isEmpty()) {
            return "";
        }
        if (low.trim().isEmpty()) {
            return "до " + high;
        }
        if (high.trim().isEmpty()) {
            return "от " + low;
        }
        return low + " — " + high;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private LabResultEntity createResult(
            long documentId,
            String testName,
            String rawValue,
            String unit,
            String referenceLow,
            String referenceHigh,
            String referenceText
    ) {
        TestNameNormalizer.NormalizedTest normalizedTest = TestNameNormalizer.normalize(testName);
        String cleanedUnit = clean(unit);
        return new LabResultEntity(
                documentId,
                clean(testName),
                normalizedTest.key,
                normalizedTest.displayName,
                clean(rawValue),
                ResultValueParser.normalizeNumber(rawValue),
                cleanedUnit,
                UnitNormalizer.normalize(cleanedUnit),
                clean(referenceLow),
                clean(referenceHigh),
                clean(referenceText),
                1.0
        );
    }

    private void normalizeLegacyResults() {
        try {
            for (LabResultEntity result : database.analysisDao().getResultsMissingCanonicalData()) {
                TestNameNormalizer.NormalizedTest normalized = TestNameNormalizer.normalize(result.testName);
                result.canonicalKey = normalized.key;
                result.canonicalName = normalized.displayName;
                result.canonicalUnit = UnitNormalizer.normalize(result.unit);
                database.analysisDao().updateResult(result);
            }
            for (LabResultEntity result : database.analysisDao().getResultsMissingParsedReference()) {
                String[] range = ResultValueParser.parseReferenceRange(result.referenceText);
                if (range[0] != null || range[1] != null) {
                    result.referenceLow = range[0];
                    result.referenceHigh = range[1];
                    database.analysisDao().updateResult(result);
                }
            }
            for (AnalysisDocumentEntity document
                    : database.analysisDao().getDocumentsMissingContentHash()) {
                try {
                    File file = requireManagedDocument(document.localPath);
                    if (file.isFile()) {
                        database.analysisDao().updateContentHash(
                                document.id,
                                DocumentHasher.sha256(file)
                        );
                    }
                } catch (IOException | SecurityException ignored) {
                    // Missing legacy files remain usable as database records.
                }
            }
        } catch (RuntimeException ignored) {
            // A later repository instance retries; user data is never deleted for normalization.
        }
    }

    private void deleteUnpersistedCopy(String localPath) {
        if (localPath == null) {
            return;
        }
        try {
            File file = requireManagedDocument(localPath);
            if (file.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (IOException | SecurityException ignored) {
            // The database was not changed; a failed best-effort cleanup is non-destructive.
        }
    }
}
