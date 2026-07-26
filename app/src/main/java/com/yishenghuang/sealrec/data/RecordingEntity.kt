package com.yishenghuang.sealrec.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yishenghuang.sealrec.core.verify.IntegrityStatus

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
)

fun IntegrityStatus.storageName(): String = name
