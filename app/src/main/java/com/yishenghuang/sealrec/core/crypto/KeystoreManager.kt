package com.yishenghuang.sealrec.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

object Sha256Hasher {
    fun digest(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun newStreaming(): MessageDigest = MessageDigest.getInstance("SHA-256")
}

/**
 * Hardware-backed ECDSA P-256 key in Android Keystore.
 * StrongBox preferred; falls back to TEE when unavailable.
 */
class KeystoreManager(
    private val alias: String = DEFAULT_ALIAS,
) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun ensureKey(): PublicKey {
        if (!keyStore.containsAlias(alias)) {
            generateKey(preferStrongBox = true)
        }
        return publicKey()
    }

    fun publicKey(): PublicKey {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.publicKey
    }

    fun publicKeyX509Bytes(): ByteArray {
        ensureKey()
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.encoded
    }

    fun signSha256Digest(digest32: ByteArray): ByteArray {
        require(digest32.size == 32) { "Expected SHA-256 digest" }
        ensureKey()
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        val sig = Signature.getInstance(SIG_ALG)
        sig.initSign(entry.privateKey)
        sig.update(digest32)
        return sig.sign()
    }

    fun verifyWithEmbeddedKey(
        digest32: ByteArray,
        signature: ByteArray,
        publicKeyX509: ByteArray,
    ): Boolean {
        return try {
            val cert = java.security.cert.CertificateFactory
                .getInstance("X.509")
                .generateCertificate(publicKeyX509.inputStream()) as X509Certificate
            verify(digest32, signature, cert.publicKey)
        } catch (_: Exception) {
            false
        }
    }

    fun verify(digest32: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        return try {
            val sig = Signature.getInstance(SIG_ALG)
            sig.initVerify(publicKey)
            sig.update(digest32)
            sig.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun generateKey(preferStrongBox: Boolean) {
        val strongBox =
            preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        try {
            createKey(strongBox = strongBox)
        } catch (_: Exception) {
            if (strongBox) {
                runCatching { keyStore.deleteEntry(alias) }
                createKey(strongBox = false)
            } else {
                throw IllegalStateException("Failed to generate Keystore key")
            }
        }
    }

    private fun createKey(strongBox: Boolean) {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "sealrec_ecdsa_v1"
        const val SIG_ALG = "SHA256withECDSA"
    }
}
