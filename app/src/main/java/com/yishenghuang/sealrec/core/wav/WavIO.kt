package com.yishenghuang.sealrec.core.wav

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class WavFormat(
    val sampleRate: Int = 44_100,
    val channels: Int = 1,
    val bitsPerSample: Int = 16,
) {
    val byteRate: Int get() = sampleRate * channels * bitsPerSample / 8
    val blockAlign: Int get() = channels * bitsPerSample / 8
}

data class WavParseResult(
    val format: WavFormat,
    val pcmData: ByteArray,
    val seal: SealPayload?,
    val headerHexPreview: String,
)

/**
 * Writes / reads PCM WAV files with an optional custom "seal" chunk between fmt and data.
 */
object WavIO {

    fun write(
        output: File,
        pcm: ByteArray,
        format: WavFormat = WavFormat(),
        seal: SealPayload? = null,
    ) {
        output.parentFile?.mkdirs()
        RandomAccessFile(output, "rw").use { raf ->
            raf.setLength(0)
            val sealBytes = seal?.let { SealChunkCodec.encode(it) }
            val sealChunkSize = if (sealBytes != null) 8 + sealBytes.size + (sealBytes.size % 2) else 0
            val dataPadding = pcm.size % 2
            val riffSize = 4 + // WAVE
                (8 + 16) + // fmt chunk (PCM fmt is 16 bytes)
                sealChunkSize +
                (8 + pcm.size + dataPadding)

            writeFourCc(raf, "RIFF")
            writeIntLe(raf, riffSize)
            writeFourCc(raf, "WAVE")

            // fmt
            writeFourCc(raf, "fmt ")
            writeIntLe(raf, 16)
            writeShortLe(raf, 1) // PCM
            writeShortLe(raf, format.channels.toShort())
            writeIntLe(raf, format.sampleRate)
            writeIntLe(raf, format.byteRate)
            writeShortLe(raf, format.blockAlign.toShort())
            writeShortLe(raf, format.bitsPerSample.toShort())

            // seal (optional)
            if (sealBytes != null) {
                writeFourCc(raf, SealChunkCodec.CHUNK_ID)
                writeIntLe(raf, sealBytes.size)
                raf.write(sealBytes)
                if (sealBytes.size % 2 == 1) raf.write(0)
            }

            // data
            writeFourCc(raf, "data")
            writeIntLe(raf, pcm.size)
            raf.write(pcm)
            if (dataPadding == 1) raf.write(0)
        }
    }

    fun writeFromRawFile(
        rawFile: File,
        output: File,
        format: WavFormat = WavFormat(),
        seal: SealPayload,
    ) {
        val pcm = rawFile.readBytes()
        write(output, pcm, format, seal)
    }

    fun parse(file: File): WavParseResult {
        RandomAccessFile(file, "r").use { raf ->
            val headerPreviewBytes = ByteArray(minOf(64, raf.length().toInt()))
            raf.readFully(headerPreviewBytes)
            raf.seek(0)

            require(readFourCc(raf) == "RIFF") { "Not a RIFF file" }
            readIntLe(raf) // riff size
            require(readFourCc(raf) == "WAVE") { "Not a WAVE file" }

            var format: WavFormat? = null
            var seal: SealPayload? = null
            var pcm: ByteArray? = null

            while (raf.filePointer < raf.length()) {
                if (raf.filePointer + 8 > raf.length()) break
                val id = readFourCc(raf)
                val size = readIntLe(raf)
                require(size >= 0) { "Negative chunk size" }
                val dataStart = raf.filePointer
                when (id) {
                    "fmt " -> {
                        val audioFormat = readShortLe(raf).toInt() and 0xFFFF
                        require(audioFormat == 1) { "Only PCM WAV supported" }
                        val channels = readShortLe(raf).toInt() and 0xFFFF
                        val sampleRate = readIntLe(raf)
                        readIntLe(raf) // byte rate
                        readShortLe(raf) // block align
                        val bits = readShortLe(raf).toInt() and 0xFFFF
                        format = WavFormat(sampleRate, channels, bits)
                        raf.seek(dataStart + size + (size % 2))
                    }
                    SealChunkCodec.CHUNK_ID -> {
                        val bytes = ByteArray(size)
                        raf.readFully(bytes)
                        seal = SealChunkCodec.decode(bytes)
                        raf.seek(dataStart + size + (size % 2))
                    }
                    "data" -> {
                        pcm = ByteArray(size)
                        raf.readFully(pcm)
                        raf.seek(dataStart + size + (size % 2))
                    }
                    else -> {
                        raf.seek(dataStart + size + (size % 2))
                    }
                }
            }

            requireNotNull(format) { "Missing fmt chunk" }
            requireNotNull(pcm) { "Missing data chunk" }

            return WavParseResult(
                format = format,
                pcmData = pcm,
                seal = seal,
                headerHexPreview = Fingerprint.toHex(headerPreviewBytes),
            )
        }
    }

    private fun writeFourCc(raf: RandomAccessFile, id: String) {
        require(id.length == 4)
        raf.write(id.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun readFourCc(raf: RandomAccessFile): String {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return String(buf, StandardCharsets.US_ASCII)
    }

    private fun writeIntLe(raf: RandomAccessFile, value: Int) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        raf.write(buf)
    }

    private fun readIntLe(raf: RandomAccessFile): Int {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun writeShortLe(raf: RandomAccessFile, value: Short) {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
        raf.write(buf)
    }

    private fun readShortLe(raf: RandomAccessFile): Short {
        val buf = ByteArray(2)
        raf.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).short
    }
}
