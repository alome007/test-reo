package dev.uclip.crypto

import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.Hash

/**
 * libsodium-backed crypto:
 *   - X25519 key agreement for pairing (crypto_box_beforenm).
 *   - XChaCha20-Poly1305-IETF AEAD for payloads (24-byte nonces so we can
 *     happily generate nonces randomly without coordination).
 *   - SHA-256 for content hashes + dedupe.
 *
 * Parameterised over a [LazySodium] instance so the Android and desktop
 * `actual` factories can supply their platform-specific Sodium binding.
 */
class LibsodiumCrypto(private val lazy: LazySodium) : Crypto {

    override fun generateKeyPair(): KeyPair {
        val pub = ByteArray(Box.PUBLICKEYBYTES)
        val priv = ByteArray(Box.SECRETKEYBYTES)
        require(lazy.cryptoBoxKeypair(pub, priv)) { "cryptoBoxKeypair failed" }
        return KeyPair(publicKey = pub, privateKey = priv)
    }

    override fun deriveSharedKey(ourPrivate: ByteArray, theirPublic: ByteArray): ByteArray {
        require(ourPrivate.size == Box.SECRETKEYBYTES) { "bad private key length" }
        require(theirPublic.size == Box.PUBLICKEYBYTES) { "bad public key length" }
        val shared = ByteArray(Box.BEFORENMBYTES)
        require(lazy.cryptoBoxBeforeNm(shared, theirPublic, ourPrivate)) { "cryptoBoxBeforeNm failed" }
        return shared
    }

    override fun seal(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): SealedPayload {
        require(key.size == AEAD.XCHACHA20POLY1305_IETF_KEYBYTES) { "key must be 32 bytes" }
        val nonce = randomBytes(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher = ByteArray(plaintext.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val cipherLen = LongArray(1)
        val ok = lazy.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            cipher, cipherLen,
            plaintext, plaintext.size.toLong(),
            associatedData, associatedData.size.toLong(),
            null,
            nonce, key,
        )
        require(ok) { "aead encrypt failed" }
        val out = if (cipherLen[0].toInt() == cipher.size) cipher else cipher.copyOf(cipherLen[0].toInt())
        return SealedPayload(nonce = nonce, ciphertext = out)
    }

    override fun open(key: ByteArray, payload: SealedPayload, associatedData: ByteArray): ByteArray {
        require(key.size == AEAD.XCHACHA20POLY1305_IETF_KEYBYTES) { "key must be 32 bytes" }
        require(payload.nonce.size == AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES) { "bad nonce length" }
        require(payload.ciphertext.size >= AEAD.XCHACHA20POLY1305_IETF_ABYTES) { "ciphertext too short" }
        val plain = ByteArray(payload.ciphertext.size - AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val plainLen = LongArray(1)
        val ok = lazy.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            plain, plainLen,
            null,
            payload.ciphertext, payload.ciphertext.size.toLong(),
            associatedData, associatedData.size.toLong(),
            payload.nonce, key,
        )
        require(ok) { "aead decrypt failed (auth tag mismatch?)" }
        return if (plainLen[0].toInt() == plain.size) plain else plain.copyOf(plainLen[0].toInt())
    }

    override fun randomBytes(n: Int): ByteArray {
        val out = ByteArray(n)
        lazy.randomBytesBuf(out, n.toLong())
        return out
    }

    override fun sha256(data: ByteArray): ByteArray {
        val out = ByteArray(Hash.SHA256_BYTES)
        require(lazy.cryptoHashSha256(out, data, data.size.toLong())) { "sha256 failed" }
        return out
    }
}
