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

interface PcmCapture {
    val bufferSizeBytes: Int
    fun start()
    fun read(buffer: ByteArray): Int
    fun stop()
}

class AudioRecordCapture(
    private val config: AudioConfig = AudioConfig(),
) : PcmCapture {
    private var recorder: AudioRecord? = null

    override val bufferSizeBytes: Int get() = config.minBufferSize()

    // The service catches SecurityException and retains recoverable PCM if permission is revoked.
    @android.annotation.SuppressLint("MissingPermission")
    @Synchronized override fun start() {
        stop()
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            config.channelConfig,
            config.encoding,
            bufferSizeBytes * 2,
        )
        recorder = rec
        check(rec.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }
        rec.startRecording()
        check(rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone unavailable" }
    }

    /**
     * Reads PCM into [buffer]. Returns bytes read, or negative on error / 0 on nothing.
     */
    @Synchronized override fun read(buffer: ByteArray): Int {
        val rec = recorder ?: return -1
        if (android.os.Build.VERSION.SDK_INT >= 29 && rec.activeRecordingConfiguration?.isClientSilenced == true) {
            return AudioRecord.ERROR_INVALID_OPERATION
        }
        return rec.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
    }

    @Synchronized override fun stop() {
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
