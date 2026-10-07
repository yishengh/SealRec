package com.yishenghuang.sealrec.core.wav

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

class WavSafetyTest {
    @get:Rule val folder = TemporaryFolder()
    private fun payload() = SealPayload(deviceTimeUtcMs = 1, pcmSha256 = ByteArray(32),
        publicKeyX509 = byteArrayOf(1), signature = byteArrayOf(2))
    private fun plain(): File = folder.newFile().also { WavIO.write(it, ByteArray(320), WavFormat(16000)) }
    private fun putInt(file: File, offset: Long, value: Int) {
        RandomAccessFile(file, "rw").use { it.seek(offset); it.writeInt(Integer.reverseBytes(value)) }
    }
    private fun rejects(file: File) {
        assertThrows(Exception::class.java) { WavIO.inspect(file) }
    }

    @Test fun rejectsTruncatedDataAndForgedRiffLength() {
        val f = plain()
        RandomAccessFile(f, "rw").use { it.setLength(it.length() - 1) }
        rejects(f)
        putInt(f, 4, (f.length() - 8).toInt())
        rejects(f)
    }

    @Test fun rejectsHugeSealBeforeAllocation() {
        val f = plain()
        RandomAccessFile(f, "rw").use { it.seek(36); it.writeBytes("seal") }
        putInt(f, 40, Int.MAX_VALUE)
        rejects(f)
    }

    @Test fun rejectsInvalidFormatAndAlignment() {
        val f = plain()
        putInt(f, 24, 0)
        rejects(f)
        val g = plain()
        putInt(g, 40, 319)
        rejects(g)
    }

    @Test fun rejectsDuplicateDataChunk() {
        val f = plain()
        RandomAccessFile(f, "rw").use { it.seek(it.length()); it.writeBytes("data"); it.writeInt(0) }
        putInt(f, 4, (f.length() - 8).toInt())
        rejects(f)
    }

    @Test fun streamsTwoHoursWithExactFormatAndDurationUnder128MiBHeap() {
        val raw = folder.newFile("input.raw")
        val block = ByteArray(32000) { (it % 127).toByte() }
        val hash = MessageDigest.getInstance("SHA-256")
        raw.outputStream().use { out -> repeat(7200) { out.write(block); hash.update(block) } }
        val output = File(folder.root, "output.wav")
        WavIO.writeFromRawFile(raw, output, WavFormat(16000), payload())
        val layout = WavIO.inspect(output)
        assertEquals(7_200_000, layout.durationMs)
        assertEquals(raw.length(), layout.dataSize.toLong())
        assertArrayEquals(hash.digest(), WavIO.hashPcm(output, layout))
        assertTrue(raw.exists())
        assertFalse(File(folder.root, "output.wav.pending").exists())
    }

    @Test fun neverOverwritesExistingRecording() {
        val raw = folder.newFile("input.raw").also { it.writeBytes(ByteArray(320)) }
        val output = folder.newFile("saved.wav").also { it.writeText("keep") }
        assertThrows(IllegalArgumentException::class.java) { WavIO.writeFromRawFile(raw, output, WavFormat(), payload()) }
        assertEquals("keep", output.readText())
        assertTrue(raw.exists())
    }

    @Test fun rejectsOversizeSparseRawWithoutReadingOrDeletingIt() {
        val raw = folder.newFile("large.raw")
        RandomAccessFile(raw, "rw").use { it.setLength(WavIO.MAX_PCM_BYTES + 2) }
        val output = File(folder.root, "large.wav")
        assertThrows(IllegalArgumentException::class.java) { WavIO.writeFromRawFile(raw, output, WavFormat(), payload()) }
        assertFalse(output.exists())
        assertTrue(raw.exists())
    }
}
