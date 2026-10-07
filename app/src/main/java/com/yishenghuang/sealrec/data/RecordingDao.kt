package com.yishenghuang.sealrec.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE deletedAtMs IS NULL ORDER BY createdAtMs DESC")
    fun observeActive(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE deletedAtMs IS NOT NULL ORDER BY deletedAtMs DESC")
    fun observeTrash(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): RecordingEntity?

    @Query("SELECT fileName FROM recordings")
    suspend fun activeFileNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecordingEntity): Long

    @Query("UPDATE recordings SET lastVerifyStatus = :status WHERE id = :id")
    suspend fun updateVerifyStatus(id: Long, status: String)

    @Query(
        "UPDATE recordings SET fileName = :fileName, filePath = :filePath, fileSizeBytes = :fileSizeBytes WHERE id = :id",
    )
    suspend fun updateFileMeta(id: Long, fileName: String, filePath: String, fileSizeBytes: Long)

    @Query("UPDATE recordings SET deletedAtMs = :deletedAtMs WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAtMs: Long)

    @Query("UPDATE recordings SET deletedAtMs = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recordings WHERE deletedAtMs IS NOT NULL")
    suspend fun getTrash(): List<RecordingEntity>

    @Query("DELETE FROM recordings WHERE deletedAtMs IS NOT NULL AND deletedAtMs < :beforeMs")
    suspend fun purgeTrashOlderThan(beforeMs: Long): Int
}
