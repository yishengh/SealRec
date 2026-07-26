package com.yishenghuang.sealrec.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.yishenghuang.sealrec.core.wav.WavFormat
import kotlin.math.max

data class AudioConfig(
    val sampleRate: Int = 44_100,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
) {
    val channels: Int
        get() = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2

    val bitsPerSample: Int
        get() = if (encoding == AudioFormat.ENCODING_PCM_16BIT) 16 else 8

    fun toWavFormat(): WavFormat = WavFormat(sampleRate, channels, bitsPerSample)

    fun minBufferSize(): Int {
        val size = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        require(size > 0) { "Invalid AudioRecord buffer size: $size" }
        return max(size, sampleRate / 10 * channels * bitsPerSample / 8) // ~100ms
    }
}

class AudioRecordCapture(
    private val config: AudioConfig = AudioConfig(),
) {
    private var recorder: AudioRecord? = null

    val bufferSizeBytes: Int get() = config.minBufferSize()

    fun start() {
        stop()
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            config.channelConfig,
            config.encoding,
            bufferSizeBytes * 2,
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }
        rec.startRecording()
        recorder = rec
    }

    /**
     * Reads PCM into [buffer]. Returns bytes read, or negative on error / 0 on nothing.
     */
    fun read(buffer: ByteArray): Int {
        val rec = recorder ?: return -1
        return rec.read(buffer, 0, buffer.size)
    }

    fun stop() {
        recorder?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        recorder = null
    }
}
