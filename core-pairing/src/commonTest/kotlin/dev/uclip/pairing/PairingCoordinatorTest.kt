package dev.uclip.pairing

import dev.uclip.crypto.StubCrypto
import dev.uclip.protocol.ControlFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PairingCoordinatorTest {

    private val crypto = StubCrypto()

    @Test
    fun endToEndHandshake() {
        val desktopRegistry = PairingRegistry()
        val phoneRegistry = PairingRegistry()
        val desktop = PairingCoordinator(crypto, desktopRegistry, TimeProvider { 1L })
        val phone = PairingCoordinator(crypto, phoneRegistry, TimeProvider { 1L })

        val desktopId = DeviceIdentity.random("Mac", crypto)
        val phoneId = DeviceIdentity.random("Android", crypto)

        val offer = desktop.generateOffer(desktopId, host = "192.168.1.10", port = 6000)
        // Phone derives shared key locally from QR pubKey.
        val phonePairing = phone.initiatorPair(phoneId, offer)

        // Phone sends PairConfirm on the wire; desktop verifies.
        val confirm = ControlFrame.PairConfirm(
            token = offer.token,
            deviceId = phoneId.deviceId,
            displayName = phoneId.displayName,
            pubKey = Base64Util.encode(phoneId.publicKey),
        )
        val desktopPairing = desktop.acceptPairConfirm(desktopId, confirm)
        assertNotNull(desktopPairing)
        assertEquals(phonePairing.sharedKey, desktopPairing.sharedKey)

        // Offer is single-use.
        assertNull(desktop.acceptPairConfirm(desktopId, confirm))
    }

    @Test
    fun rejectsWrongToken() {
        val registry = PairingRegistry()
        val desktop = PairingCoordinator(crypto, registry, TimeProvider { 0L })
        val desktopId = DeviceIdentity.random("Mac", crypto)
        val phoneId = DeviceIdentity.random("Android", crypto)

        desktop.generateOffer(desktopId, host = "x", port = 1)
        val confirm = ControlFrame.PairConfirm(
            token = "forged",
            deviceId = phoneId.deviceId,
            displayName = phoneId.displayName,
            pubKey = Base64Util.encode(phoneId.publicKey),
        )
        assertNull(desktop.acceptPairConfirm(desktopId, confirm))
    }
}
