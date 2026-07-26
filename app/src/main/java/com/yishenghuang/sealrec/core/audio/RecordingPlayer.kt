package com.yishenghuang.sealrec.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlaybackState(
    val recordingId: Long? = null,
    val isPlaying: Boolean = false,
)

/**
 * Simple local WAV player for the library screen.
 */
class RecordingPlayer {
    private var player: MediaPlayer? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun toggle(recordingId: Long, file: File) {
        val current = _state.value
        if (current.recordingId == recordingId && current.isPlaying) {
            stop()
            return
        }
        play(recordingId, file)
    }

    fun play(recordingId: Long, file: File) {
        stop()
        if (!file.exists()) return
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            prepare()
            start()
        }
        player = mp
        _state.value = PlaybackState(recordingId = recordingId, isPlaying = true)
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _state.value = PlaybackState()
    }

    fun release() = stop()
}
