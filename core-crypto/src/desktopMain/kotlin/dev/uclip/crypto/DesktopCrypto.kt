package dev.uclip.crypto

// TODO(phase 4): wire lazysodium-java. Placeholder implementation to keep the
// module compiling until the crypto phase lands.
actual fun platformCrypto(): Crypto = StubCrypto()
