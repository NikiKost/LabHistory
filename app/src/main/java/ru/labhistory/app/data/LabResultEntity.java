package ru.labhistory.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "lab_results",
        foreignKeys = @ForeignKey(
                entity = AnalysisDocumentEntity.class,
                parentColumns = "id",
                childColumns = "documentId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("documentId")
)
public class LabResultEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long documentId;
    public String testName;
    public String rawValue;
    public String numericValue;
    public String unit;
    public String referenceLow;
    public String referenceHigh;
    public String referenceText;
    public double confidence;

    public LabResultEntity(
            long documentId,
            String testName,
            String rawValue,
            String numericValue,
            String unit,
            String referenceLow,
            String referenceHigh,
            String referenceText,
            double confidence
    ) {
        this.documentId = documentId;
        this.testName = testName;
        this.rawValue = rawValue;
        this.numericValue = numericValue;
        this.unit = unit;
        this.referenceLow = referenceLow;
        this.referenceHigh = referenceHigh;
        this.referenceText = referenceText;
        this.confidence = confidence;
    }
}
