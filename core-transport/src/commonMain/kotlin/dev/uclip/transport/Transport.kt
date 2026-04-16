package dev.uclip.transport

import dev.uclip.protocol.ClipChunk
import dev.uclip.protocol.ControlFrame
import kotlinx.coroutines.flow.Flow

sealed interface InboundFrame {
    data class Control(val frame: ControlFrame) : InboundFrame
    data class Data(val chunk: ClipChunk) : InboundFrame
}

interface Transport {
    val inbound: Flow<InboundFrame>
    suspend fun send(frame: ControlFrame)
    suspend fun send(chunk: ClipChunk)
    suspend fun close()
}

data class PeerAddress(
    val deviceId: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val source: Source,
) {
    enum class Source { LAN, RELAY }
}

/**
 * mDNS-backed peer discovery. Implementations filter out the local
 * [selfDeviceId] so we never list ourselves as a peer.
 */
interface Discovery {
    val peers: Flow<List<PeerAddress>>

    fun start(selfDeviceId: String)
    fun advertise(deviceId: String, displayName: String, port: Int)
    fun stopAdvertising()
    fun close()
}

const val MDNS_SERVICE_TYPE = "_uclip._tcp."
const val MDNS_SERVICE_TYPE_LOCAL = "_uclip._tcp.local."

object TxtKeys {
    const val DEVICE_ID = "deviceId"
    const val DISPLAY_NAME = "displayName"
    const val PROTO = "proto"
}

internal val ProtocolJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}
