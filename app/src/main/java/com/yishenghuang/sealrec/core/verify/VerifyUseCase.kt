package com.yishenghuang.sealrec.core.verify

import com.yishenghuang.sealrec.core.crypto.KeystoreManager
import com.yishenghuang.sealrec.core.crypto.Sha256Hasher
import com.yishenghuang.sealrec.core.wav.Fingerprint
import com.yishenghuang.sealrec.core.wav.SealPayload
import com.yishenghuang.sealrec.core.wav.WavIO
import java.io.File

enum class IntegrityStatus {
    Intact,
    Tampered,
    BadSignature,
    NotSealRec,
}

data class IntegrityReport(
    val status: IntegrityStatus,
    val fileName: String,
    val filePath: String,
    val deviceTimeUtcMs: Long?,
    val keyFingerprintHex: String?,
    val embeddedHashHex: String?,
    val computedHashHex: String?,
    val headerHexPreview: String?,
    val message: String,
    val seal: SealPayload? = null,
)

class VerifyUseCase(
    private val keystore: KeystoreManager = KeystoreManager(),
) {
    fun verify(file: File): IntegrityReport {
        if (!file.exists() || !file.isFile) {
            return IntegrityReport(
                status = IntegrityStatus.NotSealRec,
                fileName = file.name,
                filePath = file.absolutePath,
                deviceTimeUtcMs = null,
                keyFingerprintHex = null,
                embeddedHashHex = null,
                computedHashHex = null,
                headerHexPreview = null,
                message = "File not found",
            )
        }

        val parsed = try {
            WavIO.parse(file)
        } catch (e: Exception) {
            return IntegrityReport(
                status = IntegrityStatus.NotSealRec,
                fileName = file.name,
                filePath = file.absolutePath,
                deviceTimeUtcMs = null,
                keyFingerprintHex = null,
                embeddedHashHex = null,
                computedHashHex = null,
                headerHexPreview = null,
                message = "Not a valid WAV: ${e.message}",
            )
        }

        val seal = parsed.seal
        if (seal == null) {
            return IntegrityReport(
                status = IntegrityStatus.NotSealRec,
                fileName = file.name,
                filePath = file.absolutePath,
                deviceTimeUtcMs = null,
                keyFingerprintHex = null,
                embeddedHashHex = null,
                computedHashHex = null,
                headerHexPreview = parsed.headerHexPreview,
                message = "Not a SealRec file (missing seal chunk)",
            )
        }

        val computed = Sha256Hasher.digest(parsed.pcmData)
        val computedHex = Fingerprint.toHex(computed)
        val embeddedHex = Fingerprint.toHex(seal.pcmSha256)

        if (!computed.contentEquals(seal.pcmSha256)) {
            return IntegrityReport(
                status = IntegrityStatus.Tampered,
                fileName = file.name,
                filePath = file.absolutePath,
                deviceTimeUtcMs = seal.deviceTimeUtcMs,
                keyFingerprintHex = Fingerprint.toHex(seal.keyFingerprint),
                embeddedHashHex = embeddedHex,
                computedHashHex = computedHex,
                headerHexPreview = parsed.headerHexPreview,
                message = "Audio bytes tampered (hash mismatch)",
                seal = seal,
            )
        }

        val ok = keystore.verifyWithEmbeddedKey(
            digest32 = seal.pcmSha256,
            signature = seal.signature,
            publicKeyX509 = seal.publicKeyX509,
        )

        return if (ok) {
            IntegrityReport(
                status = IntegrityStatus.Intact,
                fileName = file.name,
                filePath = file.absolutePath,
                deviceTimeUtcMs = seal.deviceTimeUtcMs,
                keyFingerprintHex = Fingerprint.toHex(seal.keyFingerprint),
                embeddedHashHex = embeddedHex,
                computedHashHex = computedHex,
                headerHexPreview = parsed.headerHexPreview,
                message = "Intact: hash matches and signature is valid",
                seal = seal,
            )
        } else {
            IntegrityReport(
                status = IntegrityStatus.BadSignature,
                fileName = file.name,
                filePath = file.absolutePath,
                deviceTimeUtcMs = seal.deviceTimeUtcMs,
                keyFingerprintHex = Fingerprint.toHex(seal.keyFingerprint),
                embeddedHashHex = embeddedHex,
                computedHashHex = computedHex,
                headerHexPreview = parsed.headerHexPreview,
                message = "Bad signature or key mismatch",
                seal = seal,
            )
        }
    }
}
