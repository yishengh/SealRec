package com.yishenghuang.sealrec.core.verify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.crypto.Sha256Hasher
import com.yishenghuang.sealrec.core.wav.SealPayload
import com.yishenghuang.sealrec.core.wav.WavFormat
import com.yishenghuang.sealrec.core.wav.WavIO
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SealVerifyInstrumentedTest {

    @Test
    fun sealedWav_verifiesGreen_andTamperIsRed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "seal_tests").also { it.mkdirs() }
        val ks = KeystoreManager("sealrec_verify_test_${System.currentTimeMillis()}")
        val pcm = ByteArray(2000) { i -> (i % 200).toByte() }
        val hash = Sha256Hasher.digest(pcm)
        val sig = ks.signSha256Digest(hash)
        val payload = SealPayload(
            deviceTimeUtcMs = System.currentTimeMillis(),
            pcmSha256 = hash,
            publicKeyX509 = ks.publicKeyX509Bytes(),
            signature = sig,
        )

        val good = File(dir, "good.wav")
        WavIO.write(good, pcm, WavFormat(), payload)
        val verifier = VerifyUseCase(ks)
        assertEquals(IntegrityStatus.Intact, verifier.verify(good).status)

        val bad = File(dir, "bad.wav")
        val dirty = pcm.copyOf().also { it[5] = (it[5] + 1).toByte() }
        WavIO.write(bad, dirty, WavFormat(), payload)
        assertEquals(IntegrityStatus.Tampered, verifier.verify(bad).status)
    }
}
