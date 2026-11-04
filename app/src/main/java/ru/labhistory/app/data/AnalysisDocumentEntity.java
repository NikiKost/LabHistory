package ru.labhistory.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "analysis_documents",
        foreignKeys = @ForeignKey(
                entity = ProfileEntity.class,
                parentColumns = "id",
                childColumns = "profileId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("profileId")
)
public class AnalysisDocumentEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long profileId;
    public String labName;
    public long analysisDate;
    public String sourceType;
    public String originalFileName;
    public String localPath;
    public String importStatus;
    public long createdAt;

    public AnalysisDocumentEntity(
            long profileId,
            String labName,
            long analysisDate,
            String sourceType,
            String originalFileName,
            String localPath,
            String importStatus,
            long createdAt
    ) {
        this.profileId = profileId;
        this.labName = labName;
        this.analysisDate = analysisDate;
        this.sourceType = sourceType;
        this.originalFileName = originalFileName;
        this.localPath = localPath;
        this.importStatus = importStatus;
        this.createdAt = createdAt;
    }
}
