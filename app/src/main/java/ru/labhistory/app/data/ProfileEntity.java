package ru.labhistory.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "profiles")
public class ProfileEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String birthDate;
    public String sex;
    public long createdAt;

    public ProfileEntity(String name, String birthDate, String sex, long createdAt) {
        this.name = name;
        this.birthDate = birthDate;
        this.sex = sex;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name;
    }
}
