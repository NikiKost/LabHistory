package ru.labhistory.app.importer;

public class ImportedDocument {
    public final String originalFileName;
    public final String localPath;
    public final String sourceType;
    public final String labName;

    public ImportedDocument(
            String originalFileName,
            String localPath,
            String sourceType,
            String labName
    ) {
        this.originalFileName = originalFileName;
        this.localPath = localPath;
        this.sourceType = sourceType;
        this.labName = labName;
    }
}
