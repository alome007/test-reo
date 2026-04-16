package dev.uclip.crypto

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava

actual fun platformCrypto(): Crypto = LibsodiumCrypto(LazySodiumJava(SodiumJava()))
