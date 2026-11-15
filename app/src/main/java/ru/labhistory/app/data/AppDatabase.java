package ru.labhistory.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import androidx.annotation.NonNull;

@Database(
        entities = {
                ProfileEntity.class,
                AnalysisDocumentEntity.class,
                LabResultEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE analysis_documents ADD COLUMN ocrText TEXT");
        }
    };
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE analysis_documents ADD COLUMN analysisDateSource TEXT NOT NULL DEFAULT 'IMPORT'");
            database.execSQL("UPDATE analysis_documents SET analysisDateSource = CASE "
                    + "WHEN sourceType = 'MANUAL' THEN 'MANUAL' "
                    + "WHEN analysisDate = createdAt THEN 'IMPORT' "
                    + "ELSE 'USER' END");
            database.execSQL("ALTER TABLE lab_results ADD COLUMN canonicalKey TEXT");
            database.execSQL("ALTER TABLE lab_results ADD COLUMN canonicalName TEXT");
            database.execSQL("ALTER TABLE lab_results ADD COLUMN canonicalUnit TEXT");
        }
    };
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE analysis_documents ADD COLUMN contentHash TEXT");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_analysis_documents_profileId_contentHash "
                    + "ON analysis_documents(profileId, contentHash)");
        }
    };

    public abstract ProfileDao profileDao();

    public abstract AnalysisDao analysisDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "lab_history.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build();
                }
            }
        }
        return instance;
    }
}
