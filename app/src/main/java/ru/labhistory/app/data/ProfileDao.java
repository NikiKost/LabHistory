package ru.labhistory.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    LiveData<List<ProfileEntity>> observeAll();

    @Query("SELECT COUNT(*) FROM profiles")
    int count();

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    List<ProfileEntity> getAll();

    @Query("SELECT * FROM profiles WHERE name = :name COLLATE NOCASE ORDER BY id ASC LIMIT 1")
    ProfileEntity findByName(String name);

    @Insert
    long insert(ProfileEntity profile);
}
