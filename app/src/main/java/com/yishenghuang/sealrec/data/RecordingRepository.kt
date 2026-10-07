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
        val name = "rec_${java.util.UUID.randomUUID()}.raw"
        return File(CrashRecovery.inProgressDir(context.filesDir).also { it.mkdirs() }, name)
    }

    fun newWavFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(recordingsDir(), "SealRec_${stamp}_${java.util.UUID.randomUUID().toString().take(8)}.wav")
    }

    suspend fun registerSealedFile(
        wav: File,
        durationMs: Long,
        keyFingerprintHex: String?,
        deviceTimeUtcMs: Long?,
    ): Long = withContext(Dispatchers.IO) {
        dao.getByPath(wav.absolutePath)?.let { return@withContext it.id }
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
        require(entity.deletedAtMs != null)
        val file = privateRecordingFile(entity.filePath)
        check(!file.exists() || file.delete()) { "Unable to delete recording" }
        val sidecar = sidecarFor(file)
        check(!sidecar.exists() || sidecar.delete()) { "Unable to delete metadata" }
        dao.deleteById(id)
    }

    suspend fun purgeAllTrash(): Int = withContext(Dispatchers.IO) {
        val trash = dao.getTrash()
        trash.forEach { entity ->
            purgeFromTrash(entity.id)
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

        if (base.toByteArray(Charsets.UTF_8).size > 240 || base.any { it.isISOControl() }) return@withContext false
        val oldFile = privateRecordingFile(entity.filePath)
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
                if (target.exists() && target.canonicalPath != oldFile.canonicalPath) return@withContext false
                val temp = File(parent, ".__sealrec_rename_${System.nanoTime()}.tmp")
                if (!oldFile.renameTo(temp)) false
                else if (temp.renameTo(target)) true
                else { check(temp.renameTo(oldFile)); false }
            }
            target.exists() -> false
            else -> oldFile.renameTo(target)
        }
        if (!renamed) return@withContext false

        if (samePath) return@withContext true
        try {
            // JSON is derived metadata. Regenerate it so the embedded file name follows the rename.
            writeSidecarJson(target, entity.keyFingerprintHex, entity.deviceTimeUtcMs, entity.durationMs)
            dao.updateFileMeta(id, target.name, target.absolutePath, target.length())
        } catch (e: Exception) {
            check(target.renameTo(oldFile)) { "Unable to roll back rename" }
            sidecarFor(target).delete()
            throw e
        }
        sidecarFor(oldFile).delete()
        true
    }

    suspend fun exportToMusic(file: File): Uri? = withContext(Dispatchers.IO) {
        privateRecordingFile(file.absolutePath)
        require(file.isFile)
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
        val uri = insertExport(collection, values, file.name, Environment.DIRECTORY_MUSIC)
        var sidecarUri: Uri? = null
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            sidecarUri = exportSidecarToMusic(file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                check(resolver.update(uri, values, null, null) > 0)
            }
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            sidecarUri?.let { runCatching { resolver.delete(it, null, null) } }
            throw e
        }
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

    suspend fun shareUris(id: Long): ArrayList<Uri> = withContext(Dispatchers.IO) {
        val entity = requireNotNull(dao.getById(id))
        require(entity.deletedAtMs == null)
        val wav = privateRecordingFile(entity.filePath)
        require(wav.isFile)
        writeSidecarJson(wav, entity.keyFingerprintHex, entity.deviceTimeUtcMs, entity.durationMs)
        arrayListOf(wav, sidecarFor(wav)).mapTo(arrayListOf()) {
            androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", it)
        }
    }

    fun listIncompleteRaws(): List<File> = CrashRecovery.listIncomplete(context.filesDir)

    suspend fun repairIncomplete(raw: File): Long = withContext(Dispatchers.IO) {
        require(raw.canonicalFile.parentFile == CrashRecovery.inProgressDir(context.filesDir).canonicalFile)
        require(raw.length() > 0) { "Empty recording" }
        val format = CrashRecovery.readFormat(raw)
        val duration = (raw.length() - raw.length() % format.blockAlign) * 1000 / format.byteRate
        // A crash may happen after WAV rename or database commit but before raw acknowledgement.
        // The journal lets recovery reuse that exact result and reclaim only its incomplete copy.
        val prior = CrashRecovery.savedFileName(raw)?.let { File(recordingsDir(), it) }
        if (prior != null) {
            val pending = File(prior.parentFile, prior.name + ".pending")
            check(!pending.exists() || pending.delete()) { "Unable to remove incomplete WAV copy" }
            if (prior.isFile) {
                val report = verifyUseCase.verify(prior)
                val layout = runCatching { WavIO.inspect(prior) }.getOrNull()
                if (report.status == IntegrityStatus.Intact && layout?.format == format &&
                    layout.dataSize.toLong() == raw.length() - raw.length() % format.blockAlign) {
                    val rawHash = java.security.MessageDigest.getInstance("SHA-256")
                    raw.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = layout.dataSize
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            check(count > 0)
                            rawHash.update(buffer, 0, count)
                            remaining -= count
                        }
                    }
                    if (Fingerprint.toHex(rawHash.digest()) == report.embeddedHashHex) {
                        val id = registerSealedFile(prior, duration, report.keyFingerprintHex, report.deviceTimeUtcMs)
                        CrashRecovery.discard(raw)
                        return@withContext id
                    }
                }
            }
        }
        val wav = newWavFile()
        val payload = CrashRecovery.repairAndSeal(raw, wav, deleteSource = false)
        val id = registerSealedFile(
            wav = wav,
            durationMs = duration,
            keyFingerprintHex = Fingerprint.toHex(payload.keyFingerprint),
            deviceTimeUtcMs = payload.deviceTimeUtcMs,
        )
        CrashRecovery.discard(raw)
        id
    }

    suspend fun discardIncomplete(raw: File) = withContext(Dispatchers.IO) {
        require(raw.canonicalFile.parentFile == CrashRecovery.inProgressDir(context.filesDir).canonicalFile)
        CrashRecovery.discard(raw)
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

    private fun exportSidecarToMusic(wav: File): Uri? {
        val sidecar = sidecarFor(wav)
        if (!sidecar.exists()) return null
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
        val uri = insertExport(collection, values, sidecar.name, Environment.DIRECTORY_DOWNLOADS)
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { out ->
                sidecar.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                check(resolver.update(uri, values, null, null) > 0)
            }
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION") // DATA is required by legacy MediaStore; never set on Android 10+.
    private fun insertExport(collection: Uri, values: ContentValues, name: String, directory: String): Uri {
        var reserved: File? = null
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val folder = File(Environment.getExternalStoragePublicDirectory(directory), "SealRec")
                check(folder.isDirectory || folder.mkdirs()) { "Unable to create export directory" }
                val sourceName = File(name)
                var candidate = File(folder, name)
                var suffix = 1
                while (!candidate.createNewFile()) {
                    candidate = File(folder, "${sourceName.nameWithoutExtension} (${suffix++}).${sourceName.extension}")
                }
                reserved = candidate
                values.put(MediaStore.MediaColumns.DATA, candidate.absolutePath)
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, candidate.name)
            }
            return requireNotNull(context.contentResolver.insert(collection, values))
        } catch (e: Exception) {
            reserved?.delete()
            throw e
        }
    }

    private fun privateRecordingFile(path: String): File {
        val file = File(path).canonicalFile
        require(file.parentFile == recordingsDir().canonicalFile && file.extension.equals("wav", true)) { "Invalid recording path" }
        return file
    }

}
