package com.yishenghuang.sealrec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecordingEntity): Long

    @Query("UPDATE recordings SET lastVerifyStatus = :status WHERE id = :id")
    suspend fun updateVerifyStatus(id: Long, status: String)

    @Query("UPDATE recordings SET fileName = :fileName, filePath = :filePath, fileSizeBytes = :fileSizeBytes WHERE id = :id")
    suspend fun updateFileMeta(id: Long, fileName: String, filePath: String, fileSizeBytes: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
