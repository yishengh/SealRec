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
import com.yishenghuang.sealrec.core.wav.WavIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingRepository(
    private val context: Context,
    private val dao: RecordingDao = SealRecDatabase.get(context).recordingDao(),
    private val verifyUseCase: VerifyUseCase = VerifyUseCase(),
) {
    fun observeRecordings(): Flow<List<RecordingEntity>> = dao.observeActive()

    fun observeTrash(): Flow<List<RecordingEntity>> = dao.observeTrash()

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
        writeSidecarJson(wav, keyFingerprintHex, deviceTimeUtcMs, durationMs)
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
                deletedAtMs = null,
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

    /** Soft-delete into recycle bin (keeps file on disk). */
    suspend fun moveToTrash(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id) ?: return@withContext
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun restoreFromTrash(id: Long) = withContext(Dispatchers.IO) {
        dao.restore(id)
    }

    suspend fun purgeFromTrash(id: Long) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        File(entity.filePath).delete()
        sidecarFor(File(entity.filePath)).delete()
        dao.deleteById(id)
    }

    suspend fun purgeAllTrash(): Int = withContext(Dispatchers.IO) {
        val trash = dao.getTrash()
        trash.forEach { entity ->
            File(entity.filePath).delete()
            sidecarFor(File(entity.filePath)).delete()
            dao.deleteById(entity.id)
        }
        trash.size
    }

    suspend fun renameRecording(id: Long, rawName: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext false
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return@withContext false

        var base = trimmed
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim('.')
        if (base.isBlank()) return@withContext false
        if (!base.endsWith(".wav", ignoreCase = true)) {
            base = "$base.wav"
        }

        val oldFile = File(entity.filePath)
        val parent = oldFile.parentFile ?: recordingsDir()
        val target = File(parent, base)

        val names = dao.activeFileNames()
        val conflict = names.any {
            it != entity.fileName && it.equals(base, ignoreCase = true)
        }
        if (conflict) return@withContext false

        if (!oldFile.exists()) return@withContext false

        val samePath = target.absolutePath.equals(oldFile.absolutePath, ignoreCase = false)
        val caseOnlyChange = !samePath &&
            target.absolutePath.equals(oldFile.absolutePath, ignoreCase = true)

        val renamed = when {
            samePath -> true
            caseOnlyChange -> {
                val temp = File(parent, ".__sealrec_rename_${System.nanoTime()}.tmp")
                oldFile.renameTo(temp) && temp.renameTo(target)
            }
            target.exists() -> false
            else -> oldFile.renameTo(target)
        }
        if (!renamed) return@withContext false

        val oldSidecar = sidecarFor(oldFile)
        val newSidecar = sidecarFor(target)
        if (oldSidecar.exists()) {
            if (oldSidecar.absolutePath.equals(newSidecar.absolutePath, ignoreCase = true) &&
                oldSidecar.absolutePath != newSidecar.absolutePath
            ) {
                val tmp = File(parent, ".__sealrec_json_${System.nanoTime()}.tmp")
                oldSidecar.renameTo(tmp)
                tmp.renameTo(newSidecar)
            } else if (oldSidecar.absolutePath != newSidecar.absolutePath) {
                oldSidecar.renameTo(newSidecar)
            }
        }

        dao.updateFileMeta(
            id = id,
            fileName = target.name,
            filePath = target.absolutePath,
            fileSizeBytes = target.length(),
        )
        true
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
        // Also export sidecar JSON as Downloads/Documents via MediaStore Files if possible
        exportSidecarToMusic(file)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    suspend fun exportVerificationPackage(id: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext false
        val wav = File(entity.filePath)
        if (!wav.exists()) return@withContext false
        writeSidecarJson(
            wav = wav,
            keyFingerprintHex = entity.keyFingerprintHex,
            deviceTimeUtcMs = entity.deviceTimeUtcMs,
            durationMs = entity.durationMs,
        )
        exportToMusic(wav) != null
    }

    fun listIncompleteRaws(): List<File> = CrashRecovery.listIncomplete(context.filesDir)

    suspend fun repairIncomplete(raw: File): Long = withContext(Dispatchers.IO) {
        require(raw.length() > 0) { "Empty recording" }
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

    private fun sidecarFor(wav: File): File =
        File(wav.parentFile, wav.nameWithoutExtension + ".json")

    private fun writeSidecarJson(
        wav: File,
        keyFingerprintHex: String?,
        deviceTimeUtcMs: Long?,
        durationMs: Long,
    ) {
        val layout = runCatching { WavIO.inspect(wav) }.getOrNull()
        val hashHex = layout?.seal?.let { Fingerprint.toHex(it.pcmSha256) }
        val json = JSONObject()
            .put("format", "SealRec-Verification-1")
            .put("fileName", wav.name)
            .put("durationMs", durationMs)
            .put("deviceTimeUtcMs", deviceTimeUtcMs)
            .put("keyFingerprint", keyFingerprintHex)
            .put("pcmSha256", hashHex)
            .put(
                "disclaimer",
                "DeviceTime is local device clock, not a trusted TSA. " +
                    "SealRec proves PCM integrity and key signature only.",
            )
        sidecarFor(wav).writeText(json.toString(2))
    }

    private fun exportSidecarToMusic(wav: File) {
        val sidecar = sidecarFor(wav)
        if (!sidecar.exists()) return
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sidecar.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SealRec")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            sidecar.inputStream().use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun estimateDurationMs(rawBytes: Long): Long {
        return (rawBytes * 1000L) / 88_200L
    }
}
