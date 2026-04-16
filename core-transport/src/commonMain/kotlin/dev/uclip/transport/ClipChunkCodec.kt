package dev.uclip.transport

import dev.uclip.protocol.ClipChunk

/**
 * Binary wire format for ClipChunk:
 *   u16 BE  clipId length (bytes, UTF-8)
 *   bytes   clipId UTF-8
 *   i32 BE  sequence number
 *   i32 BE  ciphertext length
 *   bytes   ciphertext
 *
 * Header is 10 bytes + clipId. Length prefixes mean we can stream chunks back
 * to back over a single binary WebSocket frame if we ever batch them.
 */
internal object ClipChunkCodec {
    private const val MIN_HEADER = 2 + 4 + 4

    fun encode(chunk: ClipChunk): ByteArray {
        val idBytes = chunk.clipId.encodeToByteArray()
        require(idBytes.size <= 0xFFFF) { "clipId too long" }
        val out = ByteArray(MIN_HEADER + idBytes.size + chunk.ciphertext.size)
        var p = 0
        out[p++] = (idBytes.size ushr 8).toByte()
        out[p++] = idBytes.size.toByte()
        idBytes.copyInto(out, p); p += idBytes.size
        writeInt(out, p, chunk.seq); p += 4
        writeInt(out, p, chunk.ciphertext.size); p += 4
        chunk.ciphertext.copyInto(out, p)
        return out
    }

    fun decode(bytes: ByteArray): ClipChunk {
        require(bytes.size >= MIN_HEADER) { "frame too short" }
        var p = 0
        val idLen = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF); p += 2
        require(bytes.size >= MIN_HEADER + idLen) { "frame truncated" }
        val clipId = bytes.decodeToString(p, p + idLen); p += idLen
        val seq = readInt(bytes, p); p += 4
        val cipherLen = readInt(bytes, p); p += 4
        require(cipherLen >= 0 && p + cipherLen <= bytes.size) { "bad ciphertext length" }
        val cipher = bytes.copyOfRange(p, p + cipherLen)
        return ClipChunk(clipId, seq, cipher)
    }

    private fun writeInt(out: ByteArray, p: Int, v: Int) {
        out[p] = (v ushr 24).toByte()
        out[p + 1] = (v ushr 16).toByte()
        out[p + 2] = (v ushr 8).toByte()
        out[p + 3] = v.toByte()
    }

    private fun readInt(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 24) or
            ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or
            (b[p + 3].toInt() and 0xFF)
}
