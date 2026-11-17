package ru.labhistory.app.domain;

public final class BackupFreshness {
    public enum State {
        EMPTY,
        NEVER_BACKED_UP,
        CURRENT,
        CHANGED
    }

    public final State state;
    public final int currentDocuments;
    public final int backedUpDocuments;
    public final int newDocuments;

    private BackupFreshness(
            State state,
            int currentDocuments,
            int backedUpDocuments,
            int newDocuments
    ) {
        this.state = state;
        this.currentDocuments = currentDocuments;
        this.backedUpDocuments = backedUpDocuments;
        this.newDocuments = newDocuments;
    }

    public static BackupFreshness from(
            int currentDocuments,
            long lastBackupAt,
            int backedUpDocuments
    ) {
        int safeCurrent = Math.max(0, currentDocuments);
        int safeBackedUp = Math.max(0, backedUpDocuments);
        if (safeCurrent == 0) {
            return new BackupFreshness(State.EMPTY, 0, safeBackedUp, 0);
        }
        if (lastBackupAt <= 0L) {
            return new BackupFreshness(
                    State.NEVER_BACKED_UP,
                    safeCurrent,
                    safeBackedUp,
                    safeCurrent
            );
        }
        if (safeCurrent == safeBackedUp) {
            return new BackupFreshness(
                    State.CURRENT,
                    safeCurrent,
                    safeBackedUp,
                    0
            );
        }
        return new BackupFreshness(
                State.CHANGED,
                safeCurrent,
                safeBackedUp,
                Math.max(0, safeCurrent - safeBackedUp)
        );
    }

    public boolean needsBackup() {
        return state == State.NEVER_BACKED_UP || state == State.CHANGED;
    }

    public String reminderSignature(long lastBackupAt) {
        return state.name()
                + ":" + Math.max(0L, lastBackupAt)
                + ":" + backedUpDocuments
                + ":" + currentDocuments;
    }
}
