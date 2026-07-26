package com.yishenghuang.sealrec.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yishenghuang.sealrec.core.pipeline.CrashRecovery
import com.yishenghuang.sealrec.core.verify.IntegrityStatus
import com.yishenghuang.sealrec.core.verify.VerifyUseCase
import com.yishenghuang.sealrec.core.wav.Fingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingRepository(
    private val context: Context,
    private val dao: RecordingDao = SealRecDatabase.get(context).recordingDao(),
    private val verifyUseCase: VerifyUseCase = VerifyUseCase(),
) {
    fun observeRecordings(): Flow<List<RecordingEntity>> = dao.observeAll()

    fun recordingsDir(): File = File(context.filesDir, "recordings").also { it.mkdirs() }

    fun newRawFile(): File {
        val name = "rec_${System.currentTimeMillis()}.raw"
        return File(CrashRecovery.inProgressDir(context.filesDir).also { it.mkdirs() }, name)
    }

    fun newWavFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(recordingsDir(), "SealRec_$stamp.wav")
    }

    suspend fun registerSealedFile(
        wav: File,
        durationMs: Long,
        keyFingerprintHex: String?,
        deviceTimeUtcMs: Long?,
    ): Long = withContext(Dispatchers.IO) {
        dao.upsert(
            RecordingEntity(
                fileName = wav.name,
                filePath = wav.absolutePath,
                durationMs = durationMs,
                fileSizeBytes = wav.length(),
                createdAtMs = System.currentTimeMillis(),
                keyFingerprintHex = keyFingerprintHex,
                lastVerifyStatus = IntegrityStatus.Intact.name,
                deviceTimeUtcMs = deviceTimeUtcMs,
            ),
        )
    }

    suspend fun verifyRecording(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        val report = verifyUseCase.verify(File(entity.filePath))
        dao.updateVerifyStatus(id, report.status.name)
        report
    }

    suspend fun verifyFile(file: File) = withContext(Dispatchers.IO) {
        verifyUseCase.verify(file)
    }

    suspend fun deleteRecording(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        File(entity.filePath).delete()
        dao.deleteById(id)
    }

    suspend fun exportToMusic(file: File): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SealRec")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    fun listIncompleteRaws(): List<File> = CrashRecovery.listIncomplete(context.filesDir)

    suspend fun repairIncomplete(raw: File): Long = withContext(Dispatchers.IO) {
        val wav = newWavFile()
        val payload = CrashRecovery.repairAndSeal(raw, wav)
        registerSealedFile(
            wav = wav,
            durationMs = estimateDurationMs(raw.length()),
            keyFingerprintHex = Fingerprint.toHex(payload.keyFingerprint),
            deviceTimeUtcMs = payload.deviceTimeUtcMs,
        )
    }

    suspend fun discardIncomplete(raw: File) = withContext(Dispatchers.IO) {
        raw.delete()
    }

    private fun estimateDurationMs(rawBytes: Long): Long {
        // 44100 Hz mono 16-bit = 88200 bytes/sec
        return (rawBytes * 1000L) / 88_200L
    }
}
