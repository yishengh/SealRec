package com.yishenghuang.sealrec.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreManagerInstrumentedTest {

    @Test
    fun generateSignAndVerify_roundTrip() {
        val alias = "sealrec_test_${System.currentTimeMillis()}"
        val ks = KeystoreManager(alias)
        ks.ensureKey()

        val digest = Sha256Hasher.digest("sealrec-poc".toByteArray())
        val signature = ks.signSha256Digest(digest)
        val pubBytes = ks.publicKeyX509Bytes()

        assertTrue(ks.verifyWithEmbeddedKey(digest, signature, pubBytes))
        assertTrue(ks.verify(digest, signature, ks.publicKey()))

        val tampered = digest.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(ks.verifyWithEmbeddedKey(tampered, signature, pubBytes))
    }

    @Test
    fun publicKeyX509_isStableForAlias() {
        val alias = "sealrec_stable_${System.currentTimeMillis()}"
        val ks = KeystoreManager(alias)
        val a = ks.publicKeyX509Bytes()
        val b = ks.publicKeyX509Bytes()
        assertEquals(a.toList(), b.toList())
    }
}
