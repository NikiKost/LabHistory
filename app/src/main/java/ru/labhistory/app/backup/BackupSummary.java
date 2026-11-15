package ru.labhistory.app.backup;

public final class BackupSummary {
    public final int profiles;
    public final int documents;
    public final int results;
    public final int skippedDuplicates;

    public BackupSummary(int profiles, int documents, int results, int skippedDuplicates) {
        this.profiles = profiles;
        this.documents = documents;
        this.results = results;
        this.skippedDuplicates = skippedDuplicates;
    }
}
