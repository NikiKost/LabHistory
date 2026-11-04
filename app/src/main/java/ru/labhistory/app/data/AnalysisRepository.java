package ru.labhistory.app.data;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.labhistory.app.domain.ManualResultInput;
import ru.labhistory.app.importer.DocumentImporter;
import ru.labhistory.app.importer.ImportedDocument;

public class AnalysisRepository {
    public interface Callback<T> {
        void onSuccess(T value);

        void onError(Exception error);
    }

    private final AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DocumentImporter documentImporter = new DocumentImporter();

    public AnalysisRepository(Context context) {
        database = AppDatabase.getInstance(context);
    }

    public LiveData<List<ProfileEntity>> observeProfiles() {
        return database.profileDao().observeAll();
    }

    public LiveData<List<AnalysisDocumentEntity>> observeDocuments(long profileId) {
        return database.analysisDao().observeDocuments(profileId);
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

    public void addProfile(String name) {
        executor.execute(() -> database.profileDao().insert(new ProfileEntity(
                name.trim(),
                null,
                null,
                System.currentTimeMillis()
        )));
    }

    public void importDocument(
            Context context,
            long profileId,
            Uri uri,
            Callback<AnalysisDocumentEntity> callback
    ) {
        executor.execute(() -> {
            try {
                ImportedDocument imported = documentImporter.copyToPrivateStorage(context, uri);
                long now = System.currentTimeMillis();
                AnalysisDocumentEntity entity = new AnalysisDocumentEntity(
                        profileId,
                        imported.labName,
                        now,
                        imported.sourceType,
                        imported.originalFileName,
                        imported.localPath,
                        "Сохранён, ожидает распознавания",
                        now
                );
                entity.id = database.analysisDao().insertDocument(entity);
                callback.onSuccess(entity);
            } catch (Exception error) {
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
                            "Ручной ввод",
                            null,
                            "Проверено пользователем",
                            now
                    );
                    documentId[0] = database.analysisDao().insertDocument(document);

                    LabResultEntity result = new LabResultEntity(
                            documentId[0],
                            input.testName,
                            input.value,
                            normalizeNumber(input.value),
                            input.unit,
                            input.referenceLow,
                            input.referenceHigh,
                            buildReferenceText(input.referenceLow, input.referenceHigh),
                            1.0
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

    private String normalizeNumber(String rawValue) {
        String candidate = rawValue.trim().replace(',', '.');
        return candidate.matches("[-+]?\\d+(\\.\\d+)?") ? candidate : null;
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
}
