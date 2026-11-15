package ru.labhistory.app.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
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
        indices = {
                @Index("profileId"),
                @Index(value = {"profileId", "contentHash"})
        }
)
public class AnalysisDocumentEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long profileId;
    public String labName;
    public long analysisDate;
    @NonNull
    @ColumnInfo(defaultValue = "'IMPORT'")
    public String analysisDateSource;
    public String sourceType;
    public String originalFileName;
    public String localPath;
    public String contentHash;
    public String importStatus;
    public String ocrText;
    public long createdAt;

    public AnalysisDocumentEntity(
            long profileId,
            String labName,
            long analysisDate,
            String analysisDateSource,
            String sourceType,
            String originalFileName,
            String localPath,
            String contentHash,
            String importStatus,
            String ocrText,
            long createdAt
    ) {
        this.profileId = profileId;
        this.labName = labName;
        this.analysisDate = analysisDate;
        this.analysisDateSource = analysisDateSource;
        this.sourceType = sourceType;
        this.originalFileName = originalFileName;
        this.localPath = localPath;
        this.contentHash = contentHash;
        this.importStatus = importStatus;
        this.ocrText = ocrText;
        this.createdAt = createdAt;
    }
}
