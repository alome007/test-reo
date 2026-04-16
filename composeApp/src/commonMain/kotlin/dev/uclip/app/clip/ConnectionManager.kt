package dev.uclip.app.clip

import dev.uclip.pairing.Base64Util
import dev.uclip.pairing.DeviceIdentity
import dev.uclip.pairing.Pairing
import dev.uclip.pairing.PairingCoordinator
import dev.uclip.pairing.PairingRegistry
import dev.uclip.pairing.QrPayload
import dev.uclip.protocol.ControlFrame
import dev.uclip.transport.InboundFrame
import dev.uclip.transport.PeerAddress
import dev.uclip.transport.Transport
import dev.uclip.transport.WebSocketTransport
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Tracks outbound + inbound transports and hands each one to
 * [ClipSyncController] with the right per-peer AEAD key.
 *
 * Three distinct flows:
 *   - [connectTo]:            initiator side, already-paired peer (phone → mac).
 *   - [initiatePairingWithQr]: initiator side, first-time pair via QR.
 *   - [acceptIncoming]:       responder side (desktop server accept).
 */
class ConnectionManager(
    private val httpClient: HttpClient,
    private val controller: ClipSyncController,
    private val identity: DeviceIdentity,
    private val registry: PairingRegistry,
    private val coordinator: PairingCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun connectTo(peer: PeerAddress) {
        val pairing = registry.get(peer.deviceId)
        if (pairing == null) {
            _lastError.value = "Not paired with ${peer.displayName}. Pair via QR first."
            return
        }
        val transport = WebSocketTransport.connect(httpClient, peer.host, peer.port)
        transport.send(
            ControlFrame.Hello(
                deviceId = identity.deviceId,
                displayName = identity.displayName,
                pubKey = Base64Util.encode(identity.publicKey),
            )
        )
        controller.attach(peer.deviceId, transport, Base64Util.decode(pairing.sharedKey))
        _connected.value = _connected.value + peer.deviceId
    }

    suspend fun initiatePairingWithQr(qr: QrPayload) {
        val transport = WebSocketTransport.connect(httpClient, qr.host, qr.port)
        // Initiator derives shared key locally and stores the pairing first.
        val pairing = coordinator.initiatorPair(identity, qr)
        transport.send(
            ControlFrame.PairConfirm(
                token = qr.token,
                deviceId = identity.deviceId,
                displayName = identity.displayName,
                pubKey = Base64Util.encode(identity.publicKey),
            )
        )
        val ack = try {
            withTimeout(5_000) {
                transport.inbound
                    .filterIsInstance<InboundFrame.Control>()
                    .first { it.frame is ControlFrame.PairAck || it.frame is ControlFrame.PairReject }
                    .frame
            }
        } catch (t: Throwable) {
            registry.remove(qr.deviceId)
            transport.close()
            _lastError.value = "Pairing timed out: ${t.message}"
            return
        }
        if (ack is ControlFrame.PairReject) {
            registry.remove(qr.deviceId)
            transport.close()
            _lastError.value = "Pairing rejected: ${ack.reason}"
            return
        }
        controller.attach(pairing.deviceId, transport, Base64Util.decode(pairing.sharedKey))
        _connected.value = _connected.value + pairing.deviceId
    }

    /**
     * Desktop server path. Wait for the first control frame, then branch:
     *   - [ControlFrame.PairConfirm]: run the pairing check.
     *   - [ControlFrame.Hello] from a paired peer: attach with the stored key.
     *   - anything else: close.
     */
    fun acceptIncoming(transport: Transport) {
        scope.launch { handleIncoming(transport) }
    }

    private suspend fun handleIncoming(transport: Transport) {
        val firstCtrl = try {
            withTimeout(5_000) {
                transport.inbound.filterIsInstance<InboundFrame.Control>().first()
            }.frame
        } catch (t: Throwable) {
            _lastError.value = "No handshake from incoming peer: ${t.message}"
            transport.close()
            return
        }
        when (firstCtrl) {
            is ControlFrame.PairConfirm -> {
                val pairing = coordinator.acceptPairConfirm(identity, firstCtrl)
                if (pairing == null) {
                    transport.send(ControlFrame.PairReject("no active offer or bad token"))
                    transport.close()
                    return
                }
                transport.send(
                    ControlFrame.PairAck(
                        deviceId = identity.deviceId,
                        displayName = identity.displayName,
                    )
                )
                attachPaired(pairing, transport)
            }
            is ControlFrame.Hello -> {
                val pairing = registry.get(firstCtrl.deviceId)
                if (pairing == null) {
                    transport.send(ControlFrame.PairReject("unknown device — pair first"))
                    transport.close()
                    return
                }
                attachPaired(pairing, transport)
            }
            else -> {
                transport.close()
            }
        }
    }

    private suspend fun attachPaired(pairing: Pairing, transport: Transport) {
        controller.attach(pairing.deviceId, transport, Base64Util.decode(pairing.sharedKey))
        _connected.value = _connected.value + pairing.deviceId
    }

    suspend fun disconnect(peerId: String) {
        controller.detach(peerId)
        _connected.value = _connected.value - peerId
    }
}
