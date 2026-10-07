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

/** PCM location inside a WAV without loading the full payload into memory. */
data class WavLayout(
    val format: WavFormat,
    val dataOffset: Long,
    val dataSize: Int,
    val seal: SealPayload?,
    val headerHexPreview: String,
) {
    val durationMs: Int
        get() = if (format.byteRate <= 0) 0
        else ((dataSize.toLong() * 1000L) / format.byteRate).toInt()
}

/**
 * Writes / reads PCM WAV files with an optional custom "seal" chunk between fmt and data.
 */
object WavIO {
    // Keep offsets compatible with the player, and leave ample room for RIFF metadata.
    const val MAX_PCM_BYTES = 2_000_000_000L

    fun write(
        output: File,
        pcm: ByteArray,
        format: WavFormat = WavFormat(),
        seal: SealPayload? = null,
    ) {
        validateFormat(format)
        require(pcm.size % format.blockAlign == 0) { "Incomplete PCM frame" }
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
        pcmBytes: Long = rawFile.length(),
    ) {
        validateFormat(format)
        val size = pcmBytes
        require(size in 1..MAX_PCM_BYTES && size <= rawFile.length() && size % format.blockAlign == 0L) { "Invalid PCM length" }
        require(!output.exists()) { "Output already exists" }
        val pending = File(output.parentFile, output.name + ".pending")
        require(!pending.exists()) { "Pending output already exists" }
        try {
            write(pending, ByteArray(0), format, seal)
            RandomAccessFile(pending, "rw").use { raf ->
                val headerSize = raf.length()
                raf.seek(headerSize - 4)
                writeIntLe(raf, size.toInt())
                rawFile.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var remaining = size
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        check(count > 0) { "PCM truncated while saving" }
                        raf.write(buffer, 0, count)
                        remaining -= count
                    }
                }
                if (size % 2 == 1L) raf.write(0)
                raf.seek(4)
                writeIntLe(raf, (raf.length() - 8).toInt())
                raf.fd.sync()
            }
            check(pending.renameTo(output)) { "Unable to finish WAV" }
        } finally {
            pending.delete()
        }
    }

    fun hashPcm(file: File, layout: WavLayout = inspect(file)): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(layout.dataOffset)
            val buffer = ByteArray(64 * 1024)
            var remaining = layout.dataSize
            while (remaining > 0) {
                val count = minOf(buffer.size, remaining)
                raf.readFully(buffer, 0, count)
                digest.update(buffer, 0, count)
                remaining -= count
            }
        }
        return digest.digest()
    }

    private fun validateFormat(format: WavFormat) {
        require(format.channels in 1..2 && format.bitsPerSample in listOf(8, 16) &&
            format.sampleRate in 8_000..192_000) { "Unsupported PCM format" }
    }

    fun parse(file: File): WavParseResult {
        val layout = inspect(file)
        val pcm = ByteArray(layout.dataSize)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(layout.dataOffset)
            raf.readFully(pcm)
        }
        return WavParseResult(
            format = layout.format,
            pcmData = pcm,
            seal = layout.seal,
            headerHexPreview = layout.headerHexPreview,
        )
    }

    fun inspect(file: File): WavLayout {
        RandomAccessFile(file, "r").use { raf ->
            val headerPreviewBytes = ByteArray(minOf(64L, raf.length()).toInt())
            raf.readFully(headerPreviewBytes)
            raf.seek(0)

            require(readFourCc(raf) == "RIFF") { "Not a RIFF file" }
            val riffSize = readIntLe(raf).toLong() and 0xffffffffL
            require(riffSize + 8 == raf.length()) { "Invalid RIFF length" }
            require(readFourCc(raf) == "WAVE") { "Not a WAVE file" }

            var format: WavFormat? = null
            var seal: SealPayload? = null
            var dataOffset = -1L
            var dataSize = -1

            while (raf.filePointer < raf.length()) {
                require(raf.filePointer + 8 <= raf.length()) { "Truncated chunk header" }
                val id = readFourCc(raf)
                val size = readIntLe(raf)
                require(size >= 0) { "Negative chunk size" }
                val dataStart = raf.filePointer
                require(dataStart + size + (size % 2) <= raf.length()) { "Truncated chunk" }
                when (id) {
                    "fmt " -> {
                        require(format == null && size >= 16) { "Invalid or duplicate fmt chunk" }
                        val audioFormat = readShortLe(raf).toInt() and 0xFFFF
                        require(audioFormat == 1) { "Only PCM WAV supported" }
                        val channels = readShortLe(raf).toInt() and 0xFFFF
                        val sampleRate = readIntLe(raf)
                        val byteRate = readIntLe(raf)
                        val align = readShortLe(raf).toInt() and 0xffff
                        val bits = readShortLe(raf).toInt() and 0xFFFF
                        format = WavFormat(sampleRate, channels, bits)
                        validateFormat(format)
                        require(byteRate == format.byteRate && align == format.blockAlign) { "Invalid PCM rates" }
                        raf.seek(dataStart + size + (size % 2))
                    }
                    SealChunkCodec.CHUNK_ID -> {
                        require(seal == null && size in 1..200_000) { "Invalid or duplicate seal chunk" }
                        val bytes = ByteArray(size)
                        raf.readFully(bytes)
                        seal = SealChunkCodec.decode(bytes)
                        raf.seek(dataStart + size + (size % 2))
                    }
                    "data" -> {
                        require(dataOffset < 0 && size <= MAX_PCM_BYTES) { "Invalid or duplicate data chunk" }
                        dataOffset = dataStart
                        dataSize = size
                        raf.seek(dataStart + size + (size % 2))
                    }
                    else -> {
                        raf.seek(dataStart + size + (size % 2))
                    }
                }
            }

            requireNotNull(format) { "Missing fmt chunk" }
            require(dataOffset >= 0 && dataSize >= 0) { "Missing data chunk" }
            require(dataSize % format.blockAlign == 0) { "Incomplete PCM frame" }

            return WavLayout(
                format = format,
                dataOffset = dataOffset,
                dataSize = dataSize,
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
