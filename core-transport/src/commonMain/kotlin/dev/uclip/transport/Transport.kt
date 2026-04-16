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
    val host: String,
    val port: Int,
    val deviceId: String,
    val displayName: String,
    val source: Source,
) {
    enum class Source { LAN, RELAY }
}

interface Discovery {
    fun advertise(deviceId: String, displayName: String, port: Int)
    fun stopAdvertising()
    val peers: Flow<List<PeerAddress>>
}
