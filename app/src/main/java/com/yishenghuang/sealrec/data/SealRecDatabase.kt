package com.yishenghuang.sealrec.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class SealRecDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile private var instance: SealRecDatabase? = null

        fun get(context: Context): SealRecDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SealRecDatabase::class.java,
                    "sealrec.db",
                ).build().also { instance = it }
            }
        }
    }
}
