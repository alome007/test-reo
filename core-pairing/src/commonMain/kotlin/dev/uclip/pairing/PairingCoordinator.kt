package dev.uclip.pairing

import dev.uclip.crypto.Crypto
import dev.uclip.protocol.ControlFrame

/**
 * Pure-ish pairing logic shared between the two ends of the handshake.
 *
 * Flow:
 * 1. Responder (desktop) calls [generateOffer] — produces a [QrPayload] it
 *    renders as a QR code. The offer is kept in memory until it's consumed or
 *    cleared.
 * 2. Initiator (phone) scans the QR and calls [initiatorPair] to compute the
 *    shared key locally and store the [Pairing]; then sends a
 *    [ControlFrame.PairConfirm] over WebSocket with its public key + token.
 * 3. Responder receives the PairConfirm, calls [acceptPairConfirm] to verify
 *    the token, derive the shared key, store the pairing, and send back a
 *    [ControlFrame.PairAck].
 */
fun interface TimeProvider { fun now(): Long }

class PairingCoordinator(
    private val crypto: Crypto,
    private val registry: PairingRegistry,
    private val time: TimeProvider,
) {
    private var activeOffer: QrPayload? = null

    fun activeOffer(): QrPayload? = activeOffer

    fun clearOffer() { activeOffer = null }

    fun generateOffer(identity: DeviceIdentity, host: String, port: Int): QrPayload {
        val token = Base64Util.encode(crypto.randomBytes(16))
        val offer = QrPayload(
            deviceId = identity.deviceId,
            displayName = identity.displayName,
            pubKey = Base64Util.encode(identity.publicKey),
            host = host,
            port = port,
            token = token,
        )
        activeOffer = offer
        return offer
    }

    /**
     * Responder side: verify the inbound confirm against the active offer and
     * derive the shared key. Returns the finished [Pairing] or null if the
     * offer expired / token mismatched.
     */
    fun acceptPairConfirm(identity: DeviceIdentity, frame: ControlFrame.PairConfirm): Pairing? {
        val offer = activeOffer ?: return null
        if (frame.token != offer.token) return null
        val peerPub = Base64Util.decode(frame.pubKey)
        val shared = crypto.deriveSharedKey(identity.privateKey, peerPub)
        val pairing = Pairing(
            deviceId = frame.deviceId,
            displayName = frame.displayName,
            peerPubKey = frame.pubKey,
            sharedKey = Base64Util.encode(shared),
            pairedAtEpochMs = time.now(),
        )
        registry.add(pairing)
        activeOffer = null
        return pairing
    }

    /**
     * Initiator side: derive the shared key from the QR-supplied peer pubkey
     * and this device's private key, persist the pairing, and return it.
     * The caller is responsible for then sending [ControlFrame.PairConfirm]
     * over the transport.
     */
    fun initiatorPair(identity: DeviceIdentity, qr: QrPayload): Pairing {
        val peerPub = Base64Util.decode(qr.pubKey)
        val shared = crypto.deriveSharedKey(identity.privateKey, peerPub)
        val pairing = Pairing(
            deviceId = qr.deviceId,
            displayName = qr.displayName,
            peerPubKey = qr.pubKey,
            sharedKey = Base64Util.encode(shared),
            pairedAtEpochMs = time.now(),
        )
        registry.add(pairing)
        return pairing
    }
}
