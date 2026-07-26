package com.yishenghuang.sealrec.core.wav

import com.yishenghuang.sealrec.core.crypto.Sha256Hasher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WavSealChunkTest {

    @Test
    fun sealChunk_roundTrip_codec() {
        val payload = samplePayload()
        val encoded = SealChunkCodec.encode(payload)
        val decoded = SealChunkCodec.decode(encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun wav_withSeal_parsesAndKeepsPcm() {
        val dir = File("build/test-output").also { it.mkdirs() }
        val file = File(dir, "sealed.wav")
        val pcm = ByteArray(4410 * 2) { i -> (i % 251).toByte() } // 0.1s mono 16-bit
        val payload = samplePayload(pcm)
        WavIO.write(file, pcm, WavFormat(), payload)

        val parsed = WavIO.parse(file)
        assertNotNull(parsed.seal)
        assertArrayEquals(pcm, parsed.pcmData)
        assertEquals(payload, parsed.seal)
        assertEquals(44_100, parsed.format.sampleRate)
        assertTrue(parsed.headerHexPreview.startsWith("52494646")) // RIFF
        assertTrue(file.length() > pcm.size)
    }

    @Test
    fun wav_withoutSeal_parsesAsPlain() {
        val dir = File("build/test-output").also { it.mkdirs() }
        val file = File(dir, "plain.wav")
        val pcm = ByteArray(200) { 1 }
        WavIO.write(file, pcm, WavFormat(), seal = null)
        val parsed = WavIO.parse(file)
        assertNull(parsed.seal)
        assertArrayEquals(pcm, parsed.pcmData)
    }

    @Test
    fun tamper_oneByte_changesHash() {
        val pcm = ByteArray(1000) { 7 }
        val hash1 = Sha256Hasher.digest(pcm)
        pcm[10] = (pcm[10] + 1).toByte()
        val hash2 = Sha256Hasher.digest(pcm)
        assertTrue(!hash1.contentEquals(hash2))
    }

    private fun samplePayload(pcm: ByteArray = ByteArray(32) { 3 }): SealPayload {
        val hash = Sha256Hasher.digest(pcm)
        return SealPayload(
            deviceTimeUtcMs = 1_700_000_000_000L,
            pcmSha256 = hash,
            publicKeyX509 = ByteArray(64) { (it + 1).toByte() },
            signature = ByteArray(70) { (it + 9).toByte() },
        )
    }
}
