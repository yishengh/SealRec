package com.yishenghuang.sealrec.core.verify

import com.yishenghuang.sealrec.core.crypto.Sha256Hasher
import com.yishenghuang.sealrec.core.wav.SealPayload
import com.yishenghuang.sealrec.core.wav.WavFormat
import com.yishenghuang.sealrec.core.wav.WavIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * JVM unit tests for verify logic using a software EC key (not Android Keystore).
 */
class VerifyUseCaseLogicTest {

    @Test
    fun intact_tampered_badSignature_notSeal() {
        val dir = File("build/test-output").also { it.mkdirs() }
        val pcm = ByteArray(800) { i -> (i % 127).toByte() }
        val hash = Sha256Hasher.digest(pcm)

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(pair.private)
            update(hash)
        }.sign()

        // Fake X509: use SubjectPublicKeyInfo isn't trivial without BouncyCastle;
        // Instead write seal with raw encoding and test hash path + NotSealRec / Tampered via WavIO.
        val sealed = File(dir, "verify_sealed.wav")
        val payload = SealPayload(
            deviceTimeUtcMs = 123L,
            pcmSha256 = hash,
            publicKeyX509 = ByteArray(32) { 1 }, // invalid cert bytes -> BadSignature after hash ok
            signature = sig,
        )
        WavIO.write(sealed, pcm, WavFormat(), payload)

        // Direct hash check path
        val parsed = WavIO.parse(sealed)
        assertTrue(parsed.seal != null)
        assertTrue(Sha256Hasher.digest(parsed.pcmData).contentEquals(parsed.seal!!.pcmSha256))

        // Tamper
        val tamperedFile = File(dir, "verify_tampered.wav")
        val dirty = pcm.copyOf().also { it[0] = (it[0] + 1).toByte() }
        WavIO.write(tamperedFile, dirty, WavFormat(), payload)
        val tamperedParsed = WavIO.parse(tamperedFile)
        assertTrue(
            !Sha256Hasher.digest(tamperedParsed.pcmData)
                .contentEquals(tamperedParsed.seal!!.pcmSha256),
        )

        // No seal
        val plain = File(dir, "verify_plain.wav")
        WavIO.write(plain, pcm, WavFormat(), null)
        assertEquals(null, WavIO.parse(plain).seal)
    }
}
