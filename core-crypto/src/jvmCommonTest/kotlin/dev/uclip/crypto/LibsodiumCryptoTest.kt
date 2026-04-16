package dev.uclip.crypto

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class LibsodiumCryptoTest {
    private val crypto = LibsodiumCrypto(LazySodiumJava(SodiumJava()))

    @Test
    fun sharedKeyIsSymmetric() {
        val alice = crypto.generateKeyPair()
        val bob = crypto.generateKeyPair()
        val aliceShared = crypto.deriveSharedKey(alice.privateKey, bob.publicKey)
        val bobShared = crypto.deriveSharedKey(bob.privateKey, alice.publicKey)
        assertContentEquals(aliceShared, bobShared)
    }

    @Test
    fun aeadRoundTrip() {
        val alice = crypto.generateKeyPair()
        val bob = crypto.generateKeyPair()
        val key = crypto.deriveSharedKey(alice.privateKey, bob.publicKey)
        val plaintext = "hello universal clipboard".encodeToByteArray()
        val sealed = crypto.seal(key, plaintext)
        assertNotEquals(32, sealed.ciphertext.size) // MACed
        val opened = crypto.open(key, sealed)
        assertContentEquals(plaintext, opened)
    }

    @Test
    fun aeadRejectsTampering() {
        val key = crypto.deriveSharedKey(
            crypto.generateKeyPair().privateKey,
            crypto.generateKeyPair().publicKey,
        )
        val sealed = crypto.seal(key, "top secret".encodeToByteArray())
        val tampered = SealedPayload(
            nonce = sealed.nonce,
            ciphertext = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
        )
        assertFailsWith<IllegalArgumentException> { crypto.open(key, tampered) }
    }

    @Test
    fun sha256IsDeterministic() {
        val a = crypto.sha256("hello".encodeToByteArray())
        val b = crypto.sha256("hello".encodeToByteArray())
        assertContentEquals(a, b)
        assertEquals(32, a.size)
    }
}
