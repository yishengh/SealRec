package com.yishenghuang.sealrec.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.yishenghuang.sealrec.core.wav.WavIO
import com.yishenghuang.sealrec.core.wav.WavLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class PlaybackState(
    val recordingId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val error: String? = null,
)

/**
 * Plays SealRec WAV files via AudioTrack + audio focus.
 * System MediaPlayer often fails on custom RIFF `seal` chunks.
 */
class RecordingPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val gate = Mutex()
    private var track: AudioTrack? = null
    private var file: File? = null
    private var layout: WavLayout? = null
    private val byteCursor = AtomicInteger(0)
    private val paused = AtomicBoolean(false)
    private var writeJob: Job? = null
    private var progressJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus: Boolean = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                if (_state.value.isPlaying) pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun toggle(recordingId: Long, file: File) {
        val current = _state.value
        if (current.recordingId == recordingId) {
            if (current.isPlaying) pause() else resume()
            return
        }
        play(recordingId, file)
    }

    fun play(recordingId: Long, wavFile: File) {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                releaseInternal()
                if (!wavFile.exists()) {
                    _state.value = PlaybackState(error = "File not found")
                    return@withLock
                }
                if (!requestFocus()) {
                    _state.value = PlaybackState(error = "Audio focus denied")
                    return@withLock
                }
                val inspected = try {
                    WavIO.inspect(wavFile)
                } catch (e: Exception) {
                    abandonFocus()
                    _state.value = PlaybackState(error = e.message ?: "Invalid WAV")
                    return@withLock
                }
                if (inspected.dataSize <= 0) {
                    abandonFocus()
                    _state.value = PlaybackState(error = "Empty audio")
                    return@withLock
                }

                val channelMask = if (inspected.format.channels == 1) {
                    AudioFormat.CHANNEL_OUT_MONO
                } else {
                    AudioFormat.CHANNEL_OUT_STEREO
                }
                val encoding = when (inspected.format.bitsPerSample) {
                    16 -> AudioFormat.ENCODING_PCM_16BIT
                    8 -> AudioFormat.ENCODING_PCM_8BIT
                    else -> {
                        abandonFocus()
                        _state.value = PlaybackState(error = "Unsupported PCM format")
                        return@withLock
                    }
                }
                val minBuf = AudioTrack.getMinBufferSize(
                    inspected.format.sampleRate,
                    channelMask,
                    encoding,
                )
                if (minBuf <= 0) {
                    abandonFocus()
                    _state.value = PlaybackState(error = "AudioTrack unsupported")
                    return@withLock
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(inspected.format.sampleRate)
                            .setEncoding(encoding)
                            .setChannelMask(channelMask)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                    audioTrack.release()
                    abandonFocus()
                    _state.value = PlaybackState(error = "AudioTrack init failed")
                    return@withLock
                }

                file = wavFile
                layout = inspected
                byteCursor.set(0)
                paused.set(false)
                track = audioTrack
                audioTrack.play()

                _state.value = PlaybackState(
                    recordingId = recordingId,
                    isPlaying = true,
                    positionMs = 0,
                    durationMs = inspected.durationMs,
                )
                startWriteLoop()
                startProgressLoop()
            }
        }
    }

    fun pause() {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                paused.set(true)
                try {
                    track?.pause()
                } catch (_: Exception) {
                }
                _state.value = _state.value.copy(
                    isPlaying = false,
                    positionMs = positionFromCursor(),
                )
            }
        }
    }

    fun resume() {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                val t = track ?: return@withLock
                if (!hasFocus && !requestFocus()) {
                    _state.value = _state.value.copy(error = "Audio focus denied")
                    return@withLock
                }
                paused.set(false)
                try {
                    t.play()
                } catch (_: Exception) {
                }
                _state.value = _state.value.copy(isPlaying = true, error = null)
                if (writeJob?.isActive != true) startWriteLoop()
                startProgressLoop()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                val layout = layout ?: return@withLock
                val duration = layout.durationMs.coerceAtLeast(0)
                val targetMs = positionMs.coerceIn(0, duration)
                val rawBytes = ((targetMs.toLong() * layout.format.byteRate) / 1000L).toInt()
                val align = layout.format.blockAlign.coerceAtLeast(1)
                val cursor = (rawBytes - (rawBytes % align)).coerceIn(0, layout.dataSize)

                writeJob?.cancel()
                writeJob = null

                val t = track
                val wasPlaying = !paused.get() && _state.value.recordingId != null
                try {
                    t?.pause()
                    t?.flush()
                } catch (_: Exception) {
                }

                byteCursor.set(cursor)
                _state.value = _state.value.copy(positionMs = targetMs)

                if (wasPlaying) {
                    paused.set(false)
                    try {
                        t?.play()
                    } catch (_: Exception) {
                    }
                    _state.value = _state.value.copy(isPlaying = true)
                    startWriteLoop()
                    startProgressLoop()
                }
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                releaseInternal()
                _state.value = PlaybackState()
            }
        }
    }

    fun release() = stop()

    fun clearError() {
        val current = _state.value
        if (current.error != null) {
            _state.value = current.copy(error = null)
        }
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusRequest = null
        hasFocus = false
    }

    private fun startWriteLoop() {
        writeJob?.cancel()
        writeJob = scope.launch(Dispatchers.IO) {
            val wav = file ?: return@launch
            val layout = layout ?: return@launch
            val chunk = ByteArray(8192)
            RandomAccessFile(wav, "r").use { raf ->
                while (isActive) {
                    val audioTrack = track ?: break
                    if (paused.get()) {
                        delay(40)
                        continue
                    }
                    val cursor = byteCursor.get()
                    if (cursor >= layout.dataSize) {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            positionMs = layout.durationMs,
                        )
                        break
                    }

                    val remaining = layout.dataSize - cursor
                    val toRead = minOf(chunk.size, remaining)
                    val align = layout.format.blockAlign.coerceAtLeast(1)
                    val aligned = toRead - (toRead % align)
                    if (aligned <= 0) break

                    val read = try {
                        raf.seek(layout.dataOffset + cursor)
                        raf.read(chunk, 0, aligned)
                    } catch (_: Exception) {
                        _state.value = _state.value.copy(isPlaying = false, error = "Read failed")
                        break
                    }
                    if (read <= 0) break

                    var offset = 0
                    var writtenTotal = 0
                    while (offset < read && isActive && !paused.get()) {
                        val written = try {
                            audioTrack.write(chunk, offset, read - offset)
                        } catch (_: Exception) {
                            -1
                        }
                        if (written < 0) {
                            _state.value = _state.value.copy(
                                isPlaying = false,
                                error = "Audio write failed",
                            )
                            return@use
                        }
                        if (written == 0) {
                            delay(10)
                            continue
                        }
                        offset += written
                        writtenTotal += written
                    }
                    if (writtenTotal > 0) byteCursor.addAndGet(writtenTotal)
                }
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val layout = layout
                if (layout != null) {
                    val cursor = byteCursor.get()
                    _state.value = _state.value.copy(
                        positionMs = positionFromCursor(),
                        durationMs = layout.durationMs,
                        isPlaying = !paused.get() && cursor < layout.dataSize && track != null,
                    )
                }
                delay(200)
            }
        }
    }

    private fun positionFromCursor(): Int {
        val layout = layout ?: return 0
        if (layout.format.byteRate <= 0) return 0
        return ((byteCursor.get().toLong() * 1000L) / layout.format.byteRate).toInt()
            .coerceIn(0, layout.durationMs)
    }

    private fun releaseInternal() {
        writeJob?.cancel()
        writeJob = null
        progressJob?.cancel()
        progressJob = null
        paused.set(false)
        byteCursor.set(0)
        try {
            track?.pause()
        } catch (_: Exception) {
        }
        try {
            track?.flush()
        } catch (_: Exception) {
        }
        try {
            track?.stop()
        } catch (_: Exception) {
        }
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
        file = null
        layout = null
        abandonFocus()
    }
}
