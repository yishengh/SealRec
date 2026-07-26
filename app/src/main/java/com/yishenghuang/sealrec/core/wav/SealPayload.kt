package com.yishenghuang.sealrec.core.wav

/**
 * Versioned seal payload embedded in a custom RIFF "seal" chunk.
 * Signature covers [pcmSha256] only (not the WAV header / seal chunk itself).
 */
data class SealPayload(
    val version: Int = VERSION,
    val deviceTimeUtcMs: Long,
    val pcmSha256: ByteArray,
    val publicKeyX509: ByteArray,
    val signature: ByteArray,
) {
    init {
        require(pcmSha256.size == 32) { "pcmSha256 must be 32 bytes" }
        require(publicKeyX509.isNotEmpty()) { "publicKeyX509 required" }
        require(signature.isNotEmpty()) { "signature required" }
    }

    val keyFingerprint: ByteArray
        get() = Fingerprint.sha256Prefix(publicKeyX509, 8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealPayload) return false
        return version == other.version &&
            deviceTimeUtcMs == other.deviceTimeUtcMs &&
            pcmSha256.contentEquals(other.pcmSha256) &&
            publicKeyX509.contentEquals(other.publicKeyX509) &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + deviceTimeUtcMs.hashCode()
        result = 31 * result + pcmSha256.contentHashCode()
        result = 31 * result + publicKeyX509.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object {
        const val VERSION = 1
        const val MAGIC = 0x5345414C // "SEAL"
    }
}

object Fingerprint {
    fun sha256Prefix(data: ByteArray, bytes: Int): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        return digest.copyOf(bytes)
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { b -> "%02X".format(b) }
}
