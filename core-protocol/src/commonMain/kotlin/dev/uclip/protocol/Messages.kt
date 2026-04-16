package dev.uclip.protocol

import kotlinx.serialization.Serializable

const val PROTOCOL_VERSION = 1
const val DEFAULT_CHUNK_SIZE = 256 * 1024

@Serializable
sealed interface ControlFrame {
    @Serializable
    data class Hello(
        val deviceId: String,
        val displayName: String,
        val pubKey: String,
        val protoVersion: Int = PROTOCOL_VERSION,
    ) : ControlFrame

    /**
     * Sent by the initiator (phone) after scanning the QR. [token] comes from
     * the QR payload and proves the sender actually scanned it. [pubKey] is
     * the initiator's X25519 public key, base64-encoded.
     */
    @Serializable
    data class PairConfirm(
        val token: String,
        val deviceId: String,
        val displayName: String,
        val pubKey: String,
    ) : ControlFrame

    /** Returned by the responder on successful pairing. */
    @Serializable
    data class PairAck(
        val deviceId: String,
        val displayName: String,
    ) : ControlFrame

    /** Returned by the responder if pairing failed (bad token, no active offer, etc). */
    @Serializable
    data class PairReject(val reason: String) : ControlFrame

    @Serializable
    data class ClipMeta(
        val clipId: String,
        val mime: String,
        val bytes: Long,
        val sha256: String,
        val chunks: Int,
    ) : ControlFrame

    @Serializable
    data class ClipAck(val clipId: String, val received: Boolean) : ControlFrame

    @Serializable
    data class ClipReject(val clipId: String, val reason: String) : ControlFrame

    @Serializable
    data object Ping : ControlFrame

    @Serializable
    data object Pong : ControlFrame
}

@Serializable
data class ClipChunk(
    val clipId: String,
    val seq: Int,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClipChunk) return false
        return clipId == other.clipId && seq == other.seq && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = clipId.hashCode()
        result = 31 * result + seq
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

object MimeTypes {
    const val TEXT_PLAIN = "text/plain"
    const val TEXT_HTML = "text/html"
    const val IMAGE_PNG = "image/png"
    const val IMAGE_JPEG = "image/jpeg"
    const val APPLICATION_OCTET = "application/octet-stream"
}
