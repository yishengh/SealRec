package com.yishenghuang.sealrec.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAtMs: Long,
    val keyFingerprintHex: String?,
    val lastVerifyStatus: String? = null,
    val deviceTimeUtcMs: Long? = null,
    /** Non-null means item is in recycle bin. */
    val deletedAtMs: Long? = null,
)
