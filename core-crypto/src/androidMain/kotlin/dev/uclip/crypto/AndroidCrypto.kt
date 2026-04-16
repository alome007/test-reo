package dev.uclip.crypto

// TODO(phase 4): wire lazysodium-android. Placeholder until crypto phase lands.
actual fun platformCrypto(): Crypto = StubCrypto()
