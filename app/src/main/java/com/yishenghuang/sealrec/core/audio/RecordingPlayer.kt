package com.yishenghuang.sealrec.core.audio

import android.content.Context
import android.media.*
import com.yishenghuang.sealrec.core.wav.WavIO
import com.yishenghuang.sealrec.core.wav.WavLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile

data class PlaybackState(val recordingId: Long? = null, val isPlaying: Boolean = false,
    val positionMs: Int = 0, val durationMs: Int = 0, val error: String? = null)

/** Stream custom-chunk WAVs without decoding or buffering the entire recording. */
class RecordingPlayer(context: Context, private val scope: CoroutineScope) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val gate = Mutex()
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()
    private var track: AudioTrack? = null
    private var file: File? = null
    private var layout: WavLayout? = null
    private var cursor = 0
    private var baseCursor = 0
    private var writer: Job? = null
    private var focus: AudioFocusRequest? = null

    private fun command(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            gate.withLock {
                try { block() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { releaseInternal(); _state.value = PlaybackState(error = "Playback unavailable") }
            }
        }
    }

    fun toggle(recordingId: Long, file: File) {
        command {
            if (_state.value.recordingId == recordingId) {
                if (_state.value.isPlaying) pauseInternal() else resumeInternal()
            } else playInternal(recordingId, file)
        }
    }
    fun play(recordingId: Long, wavFile: File) = command { playInternal(recordingId, wavFile) }

    private suspend fun playInternal(id: Long, wav: File) {
        releaseInternal()
        val inspected = WavIO.inspect(wav)
        require(inspected.dataSize > 0)
        check(requestFocus())
        val mask = if (inspected.format.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val encoding = if (inspected.format.bitsPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
        val min = AudioTrack.getMinBufferSize(inspected.format.sampleRate, mask, encoding)
        check(min > 0)
        track = AudioTrack.Builder().setAudioAttributes(attributes())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(inspected.format.sampleRate).setChannelMask(mask).setEncoding(encoding).build())
            .setBufferSizeInBytes(min * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
        check(track!!.state == AudioTrack.STATE_INITIALIZED)
        file = wav
        layout = inspected
        cursor = 0
        baseCursor = 0
        _state.value = PlaybackState(id, true, 0, inspected.durationMs)
        track!!.play()
        startWriter()
    }

    fun pause() = command { pauseInternal() }
    private suspend fun pauseInternal() {
        stopWriter()
        track?.pause()
        _state.value = _state.value.copy(isPlaying = false, positionMs = position())
        abandonFocus()
    }

    fun resume() = command { resumeInternal() }
    private fun resumeInternal() {
        val l = layout ?: return
        val t = track ?: return
        check(requestFocus())
        if (cursor >= l.dataSize && position() >= l.durationMs) {
            t.pause(); t.flush(); cursor = 0; baseCursor = 0
        }
        t.play()
        _state.value = _state.value.copy(isPlaying = true, error = null)
        startWriter()
    }

    fun seekTo(positionMs: Int) = command {
        val l = layout ?: return@command
        stopWriter()
        val playing = _state.value.isPlaying
        track?.pause(); track?.flush()
        val bytes = positionMs.coerceIn(0, l.durationMs).toLong() * l.format.byteRate / 1000
        cursor = (bytes - bytes % l.format.blockAlign).toInt().coerceIn(0, l.dataSize)
        baseCursor = cursor
        _state.value = _state.value.copy(positionMs = positionMs.coerceIn(0, l.durationMs))
        if (playing) { track?.play(); startWriter() }
    }

    fun stop() = command { releaseInternal(); _state.value = PlaybackState() }
    suspend fun stopAndWait() = withContext(Dispatchers.IO) { gate.withLock { releaseInternal(); _state.value = PlaybackState() } }
    fun release() {
        // viewModelScope may already be cancelled when onCleared runs.
        scope.launch(Dispatchers.IO + NonCancellable) { gate.withLock { releaseInternal() } }
    }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private fun requestFocus(): Boolean {
        abandonFocus()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
        focus = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun abandonFocus() { focus?.let { audioManager.abandonAudioFocusRequest(it) }; focus = null }

    private fun startWriter() {
        val t = track ?: return
        val l = layout ?: return
        val wav = file ?: return
        writer = scope.launch(Dispatchers.IO) {
            try {
                RandomAccessFile(wav, "r").use { raf ->
                    val buffer = ByteArray(8192)
                    while (isActive) {
                        if (cursor < l.dataSize) {
                            val count = minOf(buffer.size, l.dataSize - cursor)
                            raf.seek(l.dataOffset + cursor)
                            raf.readFully(buffer, 0, count)
                            val n = t.write(buffer, 0, count, AudioTrack.WRITE_NON_BLOCKING)
                            check(n >= 0)
                            cursor += n
                        }
                        val position = position()
                        _state.value = _state.value.copy(positionMs = position)
                        // Wait until queued samples actually reach the output before declaring completion.
                        val playedBytes = (t.playbackHeadPosition.toLong() and 0xffffffffL) * l.format.blockAlign + baseCursor
                        if (cursor >= l.dataSize && playedBytes >= l.dataSize) {
                            t.pause()
                            _state.value = _state.value.copy(isPlaying = false, positionMs = l.durationMs)
                            abandonFocus()
                            break
                        }
                        delay(10)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                command { releaseInternal(); _state.value = PlaybackState(error = "Playback unavailable") }
            }
        }
    }
    private fun position(): Int {
        val l = layout ?: return 0
        val frames = track?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0
        return ((baseCursor + frames * l.format.blockAlign) * 1000 / l.format.byteRate).toInt().coerceIn(0, l.durationMs)
    }
    private suspend fun stopWriter() { writer?.cancelAndJoin(); writer = null }
    private suspend fun releaseInternal() {
        stopWriter()
        track?.let { t -> runCatching { t.pause(); t.flush() }; t.release() }
        track = null; layout = null; file = null; cursor = 0; baseCursor = 0
        abandonFocus()
    }
}
