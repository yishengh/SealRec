package com.yishenghuang.sealrec.core.pipeline

import com.yishenghuang.sealrec.core.audio.AudioConfig
import com.yishenghuang.sealrec.core.audio.AudioRecordCapture
import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.crypto.Sha256Hasher
import com.yishenghuang.sealrec.core.wav.SealPayload
import com.yishenghuang.sealrec.core.wav.WavIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.sqrt

enum class SealEngineState {
    Idle,
    Recording,
    Paused,
    Finalizing,
}

/**
 * Bounded single-consumer PCM pipeline:
 * AudioRecord -> Channel(32) -> digest + raw write + RMS.
 */
class SealEngine(
    private val scope: CoroutineScope,
    private val keystore: KeystoreManager = KeystoreManager(),
    private val audioConfig: AudioConfig = AudioConfig(),
    private val channelCapacity: Int = 32,
) {
    private val _state = MutableStateFlow(SealEngineState.Idle)
    val state: StateFlow<SealEngineState> = _state.asStateFlow()

    private val _rms = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val rms: SharedFlow<Float> = _rms.asSharedFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var producerJob: Job? = null
    private var consumerJob: Job? = null
    private var channel: Channel<ByteArray>? = null
    private var capture: AudioRecordCapture? = null
    private var rawOut: BufferedOutputStream? = null
    private var digest: MessageDigest? = null
    private var rawFile: File? = null
    private var startWallMs: Long = 0L
    private var accumulatedMs: Long = 0L
    private val gate = Mutex()

    val currentRawFile: File? get() = rawFile

    fun start(rawFile: File) {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                when (_state.value) {
                    SealEngineState.Paused -> {
                        resumeInternal()
                        return@withLock
                    }
                    SealEngineState.Idle -> Unit
                    else -> error("Cannot start from ${_state.value}")
                }

                rawFile.parentFile?.mkdirs()
                if (rawFile.exists()) rawFile.delete()
                this@SealEngine.rawFile = rawFile
                accumulatedMs = 0L
                _elapsedMs.value = 0L
                digest = Sha256Hasher.newStreaming()
                rawOut = BufferedOutputStream(FileOutputStream(rawFile, false), 64 * 1024)
                channel = Channel(capacity = channelCapacity)
                capture = AudioRecordCapture(audioConfig).also { it.start() }
                startWallMs = System.currentTimeMillis()
                _state.value = SealEngineState.Recording
                startJobs()
            }
        }
    }

    fun pause() {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                if (_state.value != SealEngineState.Recording) return@withLock
                accumulatedMs += System.currentTimeMillis() - startWallMs
                _elapsedMs.value = accumulatedMs
                _state.value = SealEngineState.Paused
                stopCaptureAndDrain()
            }
        }
    }

    private fun resumeInternal() {
        channel = Channel(capacity = channelCapacity)
        capture = AudioRecordCapture(audioConfig).also { it.start() }
        startWallMs = System.currentTimeMillis()
        _state.value = SealEngineState.Recording
        startJobs()
    }

    suspend fun stopAndSeal(outputWav: File): SealPayload = withContext(Dispatchers.IO) {
        gate.withLock {
            val wasPaused = _state.value == SealEngineState.Paused
            _state.value = SealEngineState.Finalizing
            if (!wasPaused) {
                stopCaptureAndDrain()
            } else {
                rawOut?.flush()
            }

            rawOut?.close()
            rawOut = null

            val dig = digest ?: error("No digest")
            val hash = dig.digest()
            digest = null

            val pub = keystore.publicKeyX509Bytes()
            val signature = keystore.signSha256Digest(hash)
            val payload = SealPayload(
                deviceTimeUtcMs = System.currentTimeMillis(),
                pcmSha256 = hash,
                publicKeyX509 = pub,
                signature = signature,
            )

            val raw = rawFile ?: error("No raw file")
            WavIO.writeFromRawFile(raw, outputWav, audioConfig.toWavFormat(), payload)
            raw.delete()
            rawFile = null
            _state.value = SealEngineState.Idle
            _elapsedMs.value = 0L
            accumulatedMs = 0L
            payload
        }
    }

    fun cancel() {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                _state.value = SealEngineState.Idle
                stopCaptureAndDrain()
                try {
                    rawOut?.close()
                } catch (_: Exception) {
                }
                rawOut = null
                digest = null
                rawFile?.delete()
                rawFile = null
                _elapsedMs.value = 0L
                accumulatedMs = 0L
            }
        }
    }

    private fun startJobs() {
        val ch = channel ?: return
        val cap = capture ?: return

        producerJob = scope.launch(Dispatchers.IO) {
            val bufSize = cap.bufferSizeBytes
            while (isActive && _state.value == SealEngineState.Recording) {
                val buffer = ByteArray(bufSize)
                val read = cap.read(buffer)
                if (read > 0) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    ch.send(chunk)
                    _elapsedMs.value = accumulatedMs + (System.currentTimeMillis() - startWallMs)
                } else if (read < 0) {
                    break
                }
            }
        }

        consumerJob = scope.launch(Dispatchers.IO) {
            val dig = digest ?: return@launch
            val out = rawOut ?: return@launch
            for (chunk in ch) {
                dig.update(chunk)
                out.write(chunk)
                _rms.tryEmit(computeRms(chunk))
            }
        }
    }

    private suspend fun stopCaptureAndDrain() {
        producerJob?.cancel()
        producerJob = null
        capture?.stop()
        capture = null
        channel?.close()
        consumerJob?.join()
        consumerJob = null
        channel = null
        rawOut?.flush()
    }

    companion object {
        fun computeRms(pcm: ByteArray): Float {
            if (pcm.size < 2) return 0f
            var sum = 0.0
            var samples = 0
            var i = 0
            while (i + 1 < pcm.size) {
                val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                val signed = sample.toShort().toInt()
                sum += (signed * signed).toDouble()
                samples++
                i += 2
            }
            if (samples == 0) return 0f
            return (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
        }
    }
}

/**
 * Recover incomplete .raw recordings after process death.
 */
object CrashRecovery {
    const val IN_PROGRESS_DIR = "in_progress"
    const val RAW_SUFFIX = ".raw"

    fun inProgressDir(filesDir: File): File = File(filesDir, IN_PROGRESS_DIR)

    fun listIncomplete(filesDir: File): List<File> {
        val dir = inProgressDir(filesDir)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(RAW_SUFFIX) && f.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun repairAndSeal(
        rawFile: File,
        outputWav: File,
        keystore: KeystoreManager = KeystoreManager(),
        audioConfig: AudioConfig = AudioConfig(),
    ): SealPayload {
        val pcm = rawFile.readBytes()
        require(pcm.isNotEmpty()) { "Empty raw file" }
        val hash = Sha256Hasher.digest(pcm)
        val pub = keystore.publicKeyX509Bytes()
        val signature = keystore.signSha256Digest(hash)
        val payload = SealPayload(
            deviceTimeUtcMs = System.currentTimeMillis(),
            pcmSha256 = hash,
            publicKeyX509 = pub,
            signature = signature,
        )
        WavIO.write(outputWav, pcm, audioConfig.toWavFormat(), payload)
        rawFile.delete()
        return payload
    }
}
