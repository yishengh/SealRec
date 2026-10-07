package com.yishenghuang.sealrec.core.pipeline

import com.yishenghuang.sealrec.core.audio.AudioConfig
import com.yishenghuang.sealrec.core.audio.AudioRecordCapture
import com.yishenghuang.sealrec.core.audio.PcmCapture
import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.crypto.Sha256Hasher
import com.yishenghuang.sealrec.core.wav.SealPayload
import com.yishenghuang.sealrec.core.wav.WavFormat
import com.yishenghuang.sealrec.core.wav.WavIO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

enum class SealEngineState { Idle, Recording, Paused, Finalizing }
class EmptyRecordingException : IllegalStateException("Recording too short or empty")

/** One bounded worker owns PCM writes. Commands join it before closing or sealing files. */
class SealEngine(
    private val scope: CoroutineScope,
    private val keystore: KeystoreManager = KeystoreManager(),
    private val audioConfig: AudioConfig = AudioConfig(),
    private val captureFactory: () -> PcmCapture = { AudioRecordCapture(audioConfig) },
    private val availableBytes: (File) -> Long = { it.usableSpace },
    private val maxPcmBytes: Long = WavIO.MAX_PCM_BYTES,
) {
    private val _state = MutableStateFlow(SealEngineState.Idle)
    val state = _state.asStateFlow()
    private val _rms = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val rms = _rms.asSharedFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs = _elapsedMs.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    private var worker: Job? = null
    private var capture: PcmCapture? = null
    private var rawOut: FileOutputStream? = null
    private var rawFile: File? = null
    private var written = 0L
    private var digest = Sha256Hasher.newStreaming()
    private val gate = Mutex()
    val currentRawFile: File? get() = rawFile

    suspend fun start(raw: File) = withContext(Dispatchers.IO) {
        gate.withLock {
            if (_state.value == SealEngineState.Recording || _state.value == SealEngineState.Finalizing) return@withLock
            _error.value = false
            if (_state.value == SealEngineState.Idle) {
                require(!raw.exists()) { "Recording already exists" }
                raw.parentFile?.mkdirs()
                check(availableBytes(raw.parentFile!!) > STORAGE_RESERVE) { "Insufficient storage" }
                CrashRecovery.saveFormat(raw, audioConfig.toWavFormat())
                rawFile = raw
                written = 0
                digest = Sha256Hasher.newStreaming()
                _elapsedMs.value = 0
                rawOut = FileOutputStream(raw)
            }
            val cap = captureFactory()
            try {
                cap.start()
            } catch (e: Exception) {
                cap.stop()
                _state.value = SealEngineState.Paused
                throw e
            }
            capture = cap
            _state.value = SealEngineState.Recording
            worker = scope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(cap.bufferSizeBytes)
                    var nextStorageCheck = 0L
                    while (isActive) {
                        val n = cap.read(buffer)
                        if (!isActive) break
                        check(n >= 0) { "Audio input interrupted" }
                        if (n == 0) { delay(10); continue }
                        check(written + n <= maxPcmBytes) { "Recording length limit reached" }
                        if (written >= nextStorageCheck) {
                            // Final WAV temporarily coexists with raw; reserve space for both.
                            check(availableBytes(rawFile!!.parentFile!!) > written + STORAGE_RESERVE) { "Insufficient storage" }
                            nextStorageCheck = written + 1024 * 1024
                        }
                        rawOut!!.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        written += n
                        _elapsedMs.value = written * 1000 / audioConfig.toWavFormat().byteRate
                        _rms.tryEmit(computeRms(if (n == buffer.size) buffer else buffer.copyOf(n)))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Do not join this worker from itself.
                    scope.launch { runCatching { pause() }; _error.value = true }
                }
            }
        }
    }

    suspend fun pause() = withContext(Dispatchers.IO) {
        gate.withLock {
            if (_state.value != SealEngineState.Recording) return@withLock
            _state.value = SealEngineState.Paused
            stopCapture()
        }
    }

    private suspend fun stopCapture() {
        worker?.cancel()
        capture?.stop()
        capture = null
        worker?.join()
        worker = null
        rawOut?.fd?.sync()
    }

    suspend fun stopAndSeal(outputWav: File): SealPayload = withContext(Dispatchers.IO) {
        gate.withLock {
            check(_state.value == SealEngineState.Recording || _state.value == SealEngineState.Paused)
            _state.value = SealEngineState.Finalizing
            try {
                stopCapture()
                rawOut?.close()
                rawOut = null
                val raw = checkNotNull(rawFile)
                if (raw.length() < audioConfig.toWavFormat().byteRate / 10) {
                    CrashRecovery.discard(raw)
                    rawFile = null
                    throw EmptyRecordingException()
                }
                // Keep raw until database registration succeeds, so save failures remain recoverable.
                CrashRecovery.repairAndSeal(raw, outputWav, keystore, audioConfig,
                    deleteSource = false, expectedHash = digest.digest())
            } finally {
                _state.value = SealEngineState.Idle
            }
        }
    }

    suspend fun acknowledgeSaved() = withContext(Dispatchers.IO) {
        gate.withLock { rawFile?.let(CrashRecovery::discard); rawFile = null }
    }

    /** Service destruction preserves incomplete audio; explicit cancel alone discards it. */
    suspend fun close(discard: Boolean = false) = withContext(Dispatchers.IO + NonCancellable) {
        gate.withLock {
            try { stopCapture() } finally {
                rawOut?.close()
                rawOut = null
                if (discard || rawFile?.length() == 0L) rawFile?.let(CrashRecovery::discard)
                rawFile = null
                _state.value = SealEngineState.Idle
            }
        }
    }

    companion object {
        const val STORAGE_RESERVE = 8L * 1024 * 1024
        fun computeRms(pcm: ByteArray): Float {
            if (pcm.size < 2) return 0f
            var sum = 0.0
            var samples = 0
            var i = 0
            while (i + 1 < pcm.size) {
                val sample = ((pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
                sum += sample.toDouble() * sample
                samples++
                i += 2
            }
            return (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
        }
    }
}

object CrashRecovery {
    const val IN_PROGRESS_DIR = "in_progress"
    const val RAW_SUFFIX = ".raw"
    fun inProgressDir(filesDir: File) = File(filesDir, IN_PROGRESS_DIR)
    fun listIncomplete(filesDir: File): List<File> = inProgressDir(filesDir)
        .listFiles { f -> f.isFile && f.name.endsWith(RAW_SUFFIX) && f.length() > 0 }
        ?.sortedByDescending { it.lastModified() }.orEmpty()
    private fun metadata(raw: File) = File(raw.parentFile, raw.name + ".format")
    private fun destination(raw: File) = File(raw.parentFile, raw.name + ".destination")
    fun markDestination(raw: File, wav: File) {
        FileOutputStream(destination(raw)).use { it.write(wav.name.toByteArray()); it.fd.sync() }
    }
    fun savedFileName(raw: File): String? {
        val journal = destination(raw)
        if (!journal.isFile || journal.length() > 240) return null
        val name = journal.readText()
        return name.takeIf { it.matches(Regex("SealRec_[0-9]{8}_[0-9]{6}_[0-9a-f]{8}\\.wav")) }
    }
    fun saveFormat(raw: File, format: WavFormat) {
        FileOutputStream(metadata(raw)).use {
            it.write("${format.sampleRate},${format.channels},${format.bitsPerSample}".toByteArray())
            it.fd.sync()
        }
    }
    fun readFormat(raw: File): WavFormat {
        val meta = metadata(raw)
        if (!meta.exists()) return WavFormat() // Legacy recordings used the default; cannot infer old quality.
        val values = meta.readText().split(',').map { it.toInt() }
        require(values.size == 3 && values[0] in listOf(16000, 44100, 48000) && values[1] == 1 && values[2] == 16)
        return WavFormat(values[0], values[1], values[2])
    }
    fun discard(raw: File) {
        check(!raw.exists() || raw.delete()) { "Unable to remove raw recording" }
        metadata(raw).delete()
        destination(raw).delete()
    }
    fun repairAndSeal(
        rawFile: File,
        outputWav: File,
        keystore: KeystoreManager = KeystoreManager(),
        audioConfig: AudioConfig? = null,
        deleteSource: Boolean = true,
        expectedHash: ByteArray? = null,
    ): SealPayload {
        val format = audioConfig?.toWavFormat() ?: readFormat(rawFile)
        require(rawFile.length() in 1..WavIO.MAX_PCM_BYTES) { "Invalid raw recording" }
        val alignedBytes = rawFile.length() - rawFile.length() % format.blockAlign
        require(alignedBytes > 0) { "No complete PCM frames" }
        val digest = Sha256Hasher.newStreaming()
        rawFile.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var remaining = alignedBytes
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(count > 0) { "Raw recording truncated" }
                digest.update(buffer, 0, count)
                remaining -= count
            }
        }
        val hash = digest.digest()
        require(expectedHash == null || hash.contentEquals(expectedHash)) { "Captured PCM changed before sealing" }
        val payload = SealPayload(deviceTimeUtcMs = System.currentTimeMillis(), pcmSha256 = hash,
            publicKeyX509 = keystore.publicKeyX509Bytes(), signature = keystore.signSha256Digest(hash))
        markDestination(rawFile, outputWav)
        WavIO.writeFromRawFile(rawFile, outputWav, format, payload, alignedBytes)
        if (deleteSource) discard(rawFile)
        return payload
    }
}
