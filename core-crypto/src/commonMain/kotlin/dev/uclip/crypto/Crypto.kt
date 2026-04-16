package dev.uclip.crypto

data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray)

data class SealedPayload(val nonce: ByteArray, val ciphertext: ByteArray)

interface Crypto {
    fun generateKeyPair(): KeyPair
    fun deriveSharedKey(ourPrivate: ByteArray, theirPublic: ByteArray): ByteArray
    fun seal(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): SealedPayload
    fun open(key: ByteArray, payload: SealedPayload, associatedData: ByteArray = ByteArray(0)): ByteArray
    fun randomBytes(n: Int): ByteArray
    fun sha256(data: ByteArray): ByteArray
}

expect fun platformCrypto(): Crypto
