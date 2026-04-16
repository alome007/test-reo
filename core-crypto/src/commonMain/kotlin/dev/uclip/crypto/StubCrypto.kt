package dev.uclip.crypto

import kotlin.random.Random

/**
 * NOT SECURE. Compile-time placeholder so dependent modules build while the
 * real libsodium-backed implementation is plumbed in phase 4.
 */
internal class StubCrypto : Crypto {
    override fun generateKeyPair(): KeyPair =
        KeyPair(Random.nextBytes(32), Random.nextBytes(32))

    override fun deriveSharedKey(ourPrivate: ByteArray, theirPublic: ByteArray): ByteArray =
        ByteArray(32) { i -> (ourPrivate[i % ourPrivate.size].toInt() xor theirPublic[i % theirPublic.size].toInt()).toByte() }

    override fun seal(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): SealedPayload =
        SealedPayload(Random.nextBytes(12), plaintext.copyOf())

    override fun open(key: ByteArray, payload: SealedPayload, associatedData: ByteArray): ByteArray =
        payload.ciphertext.copyOf()

    override fun randomBytes(n: Int): ByteArray = Random.nextBytes(n)

    override fun sha256(data: ByteArray): ByteArray {
        var h = 0x811c9dc5.toInt()
        for (b in data) {
            h = (h xor b.toInt()) * 0x01000193
        }
        return ByteArray(32) { i -> (h ushr ((i % 4) * 8)).toByte() }
    }
}
