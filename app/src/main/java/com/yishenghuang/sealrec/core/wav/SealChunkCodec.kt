package com.yishenghuang.sealrec.core.wav

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary codec for the custom RIFF "seal" chunk payload (little-endian fields + length-prefixed blobs).
 *
 * Layout:
 *  magic(u32 BE "SEAL") | version(u16 LE) | deviceTimeUtcMs(i64 LE)
 *  | hashLen(u16) | pcmSha256 | pubLen(u16) | publicKeyX509 | sigLen(u16) | signature
 */
object SealChunkCodec {
    const val CHUNK_ID = "seal"

    fun encode(payload: SealPayload): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            // Magic as big-endian fourCC for readability in hex dumps
            out.writeInt(SealPayload.MAGIC)
            writeU16Le(out, payload.version)
            writeI64Le(out, payload.deviceTimeUtcMs)
            writeLenPrefixed(out, payload.pcmSha256)
            writeLenPrefixed(out, payload.publicKeyX509)
            writeLenPrefixed(out, payload.signature)
        }
        return bos.toByteArray()
    }

    fun decode(bytes: ByteArray): SealPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = input.readInt()
            require(magic == SealPayload.MAGIC) {
                "Invalid seal magic: 0x${magic.toString(16)}"
            }
            val version = readU16Le(input)
            require(version == SealPayload.VERSION) {
                "Unsupported seal version: $version"
            }
            val deviceTime = readI64Le(input)
            val hash = readLenPrefixed(input)
            val pub = readLenPrefixed(input)
            val sig = readLenPrefixed(input)
            return SealPayload(
                version = version,
                deviceTimeUtcMs = deviceTime,
                pcmSha256 = hash,
                publicKeyX509 = pub,
                signature = sig,
            )
        }
    }

    private fun writeU16Le(out: DataOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun readU16Le(input: DataInputStream): Int {
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private fun writeI64Le(out: DataOutputStream, value: Long) {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        out.write(buf)
    }

    private fun readI64Le(input: DataInputStream): Long {
        val buf = ByteArray(8)
        input.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun writeLenPrefixed(out: DataOutputStream, data: ByteArray) {
        require(data.size <= 0xFFFF) { "Blob too large: ${data.size}" }
        writeU16Le(out, data.size)
        out.write(data)
    }

    private fun readLenPrefixed(input: DataInputStream): ByteArray {
        val len = readU16Le(input)
        val data = ByteArray(len)
        input.readFully(data)
        return data
    }
}
