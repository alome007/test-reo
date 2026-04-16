package dev.uclip.app.clip

import dev.uclip.app.DeviceIdentity
import dev.uclip.protocol.ControlFrame
import dev.uclip.transport.PeerAddress
import dev.uclip.transport.Transport
import dev.uclip.transport.WebSocketTransport
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Tracks outbound + inbound transports and hands each one to
 * [ClipSyncController]. UI code calls [connectTo] for outbound dials (the
 * Android → Mac path for now); platform bootstrap code calls [acceptIncoming]
 * for every server-accepted session.
 */
class ConnectionManager(
    private val httpClient: HttpClient,
    private val controller: ClipSyncController,
    private val identity: DeviceIdentity,
) {
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    suspend fun connectTo(peer: PeerAddress) {
        val transport = WebSocketTransport.connect(httpClient, peer.host, peer.port)
        transport.send(
            ControlFrame.Hello(
                deviceId = identity.deviceId,
                displayName = identity.displayName,
                pubKey = PLACEHOLDER_PUBKEY,
            )
        )
        controller.attach(peer.deviceId, transport)
        _connected.value = _connected.value + peer.deviceId
    }

    suspend fun disconnect(peerId: String) {
        controller.detach(peerId)
        _connected.value = _connected.value - peerId
    }

    /**
     * Used by the desktop server path. The remote device identifies itself via
     * [ControlFrame.Hello]; until that frame lands we key the connection by a
     * random token so ClipSyncController can already start pumping frames.
     */
    suspend fun acceptIncoming(transport: Transport) {
        val tempId = "incoming-${Random.nextLong().toULong().toString(16)}"
        controller.attach(tempId, transport)
        _connected.value = _connected.value + tempId
    }
}

private const val PLACEHOLDER_PUBKEY = "phase5-placeholder"
