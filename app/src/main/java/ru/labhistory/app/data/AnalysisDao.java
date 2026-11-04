package ru.labhistory.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AnalysisDao {
    @Query("SELECT * FROM analysis_documents WHERE profileId = :profileId ORDER BY analysisDate DESC, id DESC")
    LiveData<List<AnalysisDocumentEntity>> observeDocuments(long profileId);

    @Query("SELECT * FROM lab_results WHERE documentId = :documentId ORDER BY id ASC")
    List<LabResultEntity> getResults(long documentId);

    @Insert
    long insertDocument(AnalysisDocumentEntity document);

    @Insert
    void insertResult(LabResultEntity result);
}
