package dev.uclip.pairing

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64Util {
    private val b64 = Base64.Default

    fun encode(bytes: ByteArray): String = b64.encode(bytes)
    fun decode(s: String): ByteArray = b64.decode(s)
}
