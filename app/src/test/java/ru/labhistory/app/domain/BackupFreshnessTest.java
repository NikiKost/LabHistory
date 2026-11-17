package ru.labhistory.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupFreshnessTest {
    @Test
    public void emptyDatabaseDoesNotRequireBackup() {
        BackupFreshness freshness = BackupFreshness.from(0, 0L, 0);

        assertEquals(BackupFreshness.State.EMPTY, freshness.state);
        assertFalse(freshness.needsBackup());
    }

    @Test
    public void documentsWithoutBackupRequireBackup() {
        BackupFreshness freshness = BackupFreshness.from(3, 0L, 0);

        assertEquals(BackupFreshness.State.NEVER_BACKED_UP, freshness.state);
        assertEquals(3, freshness.newDocuments);
        assertTrue(freshness.needsBackup());
    }

    @Test
    public void matchingDocumentCountIsCurrent() {
        BackupFreshness freshness = BackupFreshness.from(4, 1000L, 4);

        assertEquals(BackupFreshness.State.CURRENT, freshness.state);
        assertFalse(freshness.needsBackup());
    }

    @Test
    public void addedDocumentsMakeBackupStale() {
        BackupFreshness freshness = BackupFreshness.from(7, 1000L, 4);

        assertEquals(BackupFreshness.State.CHANGED, freshness.state);
        assertEquals(3, freshness.newDocuments);
        assertTrue(freshness.needsBackup());
    }

    @Test
    public void deletedDocumentsAlsoMakeBackupStateDifferent() {
        BackupFreshness freshness = BackupFreshness.from(2, 1000L, 4);

        assertEquals(BackupFreshness.State.CHANGED, freshness.state);
        assertEquals(0, freshness.newDocuments);
        assertTrue(freshness.needsBackup());
    }
}
