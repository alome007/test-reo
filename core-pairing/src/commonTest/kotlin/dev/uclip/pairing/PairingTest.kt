package dev.uclip.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingTest {
    @Test
    fun qrPayloadRoundTrip() {
        val p = QrPayload(
            deviceId = "d1",
            displayName = "Mac",
            pubKey = "k",
            host = "192.168.1.10",
            port = 5123,
            token = "t",
        )
        val round = QrPayload.decode(QrPayload.encode(p))
        assertEquals(p, round)
    }
}
