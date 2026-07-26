package com.yishenghuang.sealrec.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val recordingId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

/**
 * Local WAV player with pause/resume, progress, and seek.
 */
class RecordingPlayer(
    private val scope: CoroutineScope,
) {
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

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

    fun play(recordingId: Long, file: File) {
        releasePlayer()
        if (!file.exists()) return
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    positionMs = duration.coerceAtLeast(0),
                )
                stopProgressLoop()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            prepare()
            start()
        }
        player = mp
        _state.value = PlaybackState(
            recordingId = recordingId,
            isPlaying = true,
            positionMs = 0,
            durationMs = mp.duration.coerceAtLeast(0),
        )
        startProgressLoop()
    }

    fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause()
            _state.value = _state.value.copy(
                isPlaying = false,
                positionMs = mp.currentPosition,
                durationMs = mp.duration.coerceAtLeast(0),
            )
        }
        stopProgressLoop()
    }

    fun resume() {
        val mp = player ?: return
        mp.start()
        _state.value = _state.value.copy(isPlaying = true)
        startProgressLoop()
    }

    fun seekTo(positionMs: Int) {
        val mp = player ?: return
        val duration = mp.duration.coerceAtLeast(0)
        val target = positionMs.coerceIn(0, duration)
        mp.seekTo(target)
        _state.value = _state.value.copy(
            positionMs = target,
            durationMs = duration,
        )
    }

    fun stop() {
        releasePlayer()
        _state.value = PlaybackState()
    }

    fun release() = stop()

    private fun releasePlayer() {
        stopProgressLoop()
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = scope.launch {
            while (isActive) {
                val mp = player ?: break
                try {
                    _state.value = _state.value.copy(
                        isPlaying = mp.isPlaying,
                        positionMs = mp.currentPosition.coerceAtLeast(0),
                        durationMs = mp.duration.coerceAtLeast(0),
                    )
                } catch (_: Exception) {
                    break
                }
                delay(200)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }
}
