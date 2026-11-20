package ru.labhistory.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AnalysisDao {
    @Query("SELECT COUNT(*) FROM analysis_documents")
    LiveData<Integer> observeDocumentCount();

    @Query("SELECT * FROM analysis_documents WHERE profileId = :profileId ORDER BY analysisDate DESC, id DESC")
    LiveData<List<AnalysisDocumentEntity>> observeDocuments(long profileId);

    @Query("SELECT * FROM analysis_documents WHERE profileId = :profileId ORDER BY analysisDate ASC, id ASC")
    List<AnalysisDocumentEntity> getDocuments(long profileId);

    @Query("SELECT * FROM analysis_documents WHERE id = :documentId LIMIT 1")
    AnalysisDocumentEntity getDocument(long documentId);

    @Query("SELECT * FROM analysis_documents WHERE id = :documentId LIMIT 1")
    LiveData<AnalysisDocumentEntity> observeDocument(long documentId);

    @Query("SELECT id FROM analysis_documents WHERE profileId = :profileId "
            + "AND contentHash = :contentHash LIMIT 1")
    Long findDocumentIdByHash(long profileId, String contentHash);

    @Query("SELECT id FROM analysis_documents WHERE profileId = :profileId "
            + "AND sourceType = 'MANUAL' AND analysisDate = :analysisDate "
            + "AND createdAt = :createdAt LIMIT 1")
    Long findManualDocumentId(long profileId, long analysisDate, long createdAt);

    @Query("SELECT * FROM analysis_documents WHERE contentHash IS NULL AND localPath IS NOT NULL")
    List<AnalysisDocumentEntity> getDocumentsMissingContentHash();

    @Query("SELECT * FROM lab_results WHERE documentId = :documentId ORDER BY id ASC")
    List<LabResultEntity> getResults(long documentId);

    @Query("SELECT r.canonicalKey AS canonicalKey, r.canonicalName AS testName, "
            + "COALESCE(r.canonicalUnit, '') AS unit, "
            + "COUNT(*) AS measurementCount, MAX(d.analysisDate) AS latestDate, "
            + "(SELECT r2.numericValue FROM lab_results r2 "
            + "INNER JOIN analysis_documents d2 ON d2.id = r2.documentId "
            + "WHERE d2.profileId = :profileId AND r2.canonicalKey = r.canonicalKey "
            + "AND COALESCE(r2.canonicalUnit, '') = COALESCE(r.canonicalUnit, '') "
            + "AND r2.numericValue IS NOT NULL "
            + "ORDER BY d2.analysisDate DESC, r2.id DESC LIMIT 1) AS latestNumericValue, "
            + "(SELECT r2.referenceLow FROM lab_results r2 "
            + "INNER JOIN analysis_documents d2 ON d2.id = r2.documentId "
            + "WHERE d2.profileId = :profileId AND r2.canonicalKey = r.canonicalKey "
            + "AND COALESCE(r2.canonicalUnit, '') = COALESCE(r.canonicalUnit, '') "
            + "AND r2.numericValue IS NOT NULL "
            + "ORDER BY d2.analysisDate DESC, r2.id DESC LIMIT 1) AS latestReferenceLow, "
            + "(SELECT r2.referenceHigh FROM lab_results r2 "
            + "INNER JOIN analysis_documents d2 ON d2.id = r2.documentId "
            + "WHERE d2.profileId = :profileId AND r2.canonicalKey = r.canonicalKey "
            + "AND COALESCE(r2.canonicalUnit, '') = COALESCE(r.canonicalUnit, '') "
            + "AND r2.numericValue IS NOT NULL "
            + "ORDER BY d2.analysisDate DESC, r2.id DESC LIMIT 1) AS latestReferenceHigh "
            + "FROM lab_results r "
            + "INNER JOIN analysis_documents d ON d.id = r.documentId "
            + "WHERE d.profileId = :profileId AND r.numericValue IS NOT NULL "
            + "AND r.canonicalKey IS NOT NULL AND r.canonicalKey != '' "
            + "GROUP BY r.canonicalKey, COALESCE(r.canonicalUnit, '') "
            + "ORDER BY r.canonicalName COLLATE NOCASE ASC")
    List<IndicatorSummary> getIndicatorSummaries(long profileId);

    @Query("SELECT r.id AS resultId, d.analysisDate AS analysisDate, r.testName AS testName, "
            + "r.rawValue AS rawValue, "
            + "r.numericValue AS numericValue, COALESCE(r.canonicalUnit, r.unit, '') AS unit, "
            + "r.referenceLow AS referenceLow, r.referenceHigh AS referenceHigh, "
            + "r.referenceText AS referenceText, d.labName AS labName "
            + "FROM lab_results r "
            + "INNER JOIN analysis_documents d ON d.id = r.documentId "
            + "WHERE d.profileId = :profileId AND r.canonicalKey = :canonicalKey "
            + "AND COALESCE(r.canonicalUnit, '') = :unit AND r.numericValue IS NOT NULL "
            + "ORDER BY d.analysisDate ASC, r.id ASC")
    List<IndicatorMeasurement> getIndicatorMeasurements(
            long profileId,
            String canonicalKey,
            String unit
    );

    @Query("SELECT d.analysisDate AS analysisDate, d.labName AS labName, "
            + "d.originalFileName AS originalFileName, r.testName AS testName, "
            + "r.canonicalName AS canonicalName, r.rawValue AS rawValue, "
            + "r.numericValue AS numericValue, r.unit AS unit, "
            + "r.referenceLow AS referenceLow, r.referenceHigh AS referenceHigh, "
            + "r.referenceText AS referenceText "
            + "FROM lab_results r "
            + "INNER JOIN analysis_documents d ON d.id = r.documentId "
            + "WHERE d.profileId = :profileId "
            + "ORDER BY d.analysisDate ASC, COALESCE(r.canonicalName, r.testName) COLLATE NOCASE ASC, r.id ASC")
    List<ProfileExportRow> getProfileExportRows(long profileId);

    @Query("SELECT r.* FROM lab_results r "
            + "INNER JOIN analysis_documents d ON d.id = r.documentId "
            + "WHERE r.id = :resultId AND d.profileId = :profileId LIMIT 1")
    LabResultEntity getResultForProfile(long profileId, long resultId);

    @Query("SELECT * FROM lab_results WHERE canonicalKey IS NULL OR canonicalKey = ''")
    List<LabResultEntity> getResultsMissingCanonicalData();

    @Query("SELECT * FROM lab_results")
    List<LabResultEntity> getAllResultsForUnitNormalization();

    @Query("UPDATE lab_results SET canonicalUnit = :canonicalUnit WHERE id = :resultId")
    void updateCanonicalUnit(long resultId, String canonicalUnit);

    @Query("SELECT * FROM lab_results WHERE referenceText IS NOT NULL AND TRIM(referenceText) != '' "
            + "AND referenceLow IS NULL AND referenceHigh IS NULL")
    List<LabResultEntity> getResultsMissingParsedReference();

    @Insert
    long insertDocument(AnalysisDocumentEntity document);

    @Insert
    void insertResult(LabResultEntity result);

    @Update
    void updateResult(LabResultEntity result);

    @Query("DELETE FROM lab_results WHERE documentId = :documentId")
    void deleteResults(long documentId);

    @Query("DELETE FROM lab_results WHERE id = :resultId AND documentId IN "
            + "(SELECT id FROM analysis_documents WHERE profileId = :profileId)")
    int deleteResultForProfile(long profileId, long resultId);

    @Query("UPDATE analysis_documents SET importStatus = :status WHERE id = :documentId")
    void updateStatus(long documentId, String status);

    @Query("UPDATE analysis_documents SET contentHash = :contentHash WHERE id = :documentId")
    void updateContentHash(long documentId, String contentHash);

    @Query("UPDATE lab_results SET canonicalKey = :newKey, canonicalName = :newName "
            + "WHERE canonicalKey = :oldKey AND COALESCE(canonicalUnit, '') = :unit "
            + "AND documentId IN (SELECT id FROM analysis_documents WHERE profileId = :profileId)")
    int renameIndicatorGroup(
            long profileId,
            String oldKey,
            String unit,
            String newKey,
            String newName
    );

    @Query("UPDATE analysis_documents SET ocrText = :ocrText, labName = :labName, importStatus = :status WHERE id = :documentId")
    void updateOcr(long documentId, String ocrText, String labName, String status);

    @Query("UPDATE analysis_documents SET analysisDate = :analysisDate, analysisDateSource = 'USER' WHERE id = :documentId")
    void updateAnalysisDateByUser(long documentId, long analysisDate);

    @Query("UPDATE analysis_documents SET analysisDate = :analysisDate, analysisDateSource = 'DOCUMENT' "
            + "WHERE id = :documentId AND analysisDateSource IN ('IMPORT', 'DOCUMENT')")
    int updateDetectedAnalysisDate(long documentId, long analysisDate);

    @Query("DELETE FROM analysis_documents WHERE id = :documentId")
    int deleteDocument(long documentId);
}
