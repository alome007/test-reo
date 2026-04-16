package dev.uclip.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

actual fun platformCrypto(): Crypto = LibsodiumCrypto(LazySodiumAndroid(SodiumAndroid()))
