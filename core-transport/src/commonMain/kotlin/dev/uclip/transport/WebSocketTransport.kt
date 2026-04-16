package dev.uclip.transport

import dev.uclip.protocol.ClipChunk
import dev.uclip.protocol.ControlFrame
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

// TODO(phase 2): this is a skeleton. Proper framing + binary chunk decoding
// lands when the transport phase is picked up. Keeping the API shape stable so
// callers can wire the rest of the app against it.
class WebSocketTransport internal constructor(
    private val session: WebSocketSession,
) : Transport {
    private val _inbound = MutableSharedFlow<InboundFrame>(extraBufferCapacity = 64)
    override val inbound: Flow<InboundFrame> = _inbound.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    suspend fun consume() {
        try {
            session.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val ctrl = json.decodeFromString(ControlFrame.serializer(), frame.readText())
                        _inbound.emit(InboundFrame.Control(ctrl))
                    }
                    is Frame.Binary -> {
                        // TODO: decode binary ClipChunk (length-prefixed).
                        // Keeping placeholder so skeleton builds.
                        val _bytes = frame.readBytes()
                    }
                    else -> { /* ignore ping/pong/close */ }
                }
            }
        } catch (_: CancellationException) {
            throw CancellationException("transport cancelled")
        }
    }

    override suspend fun send(frame: ControlFrame) {
        session.send(json.encodeToString(ControlFrame.serializer(), frame))
    }

    override suspend fun send(chunk: ClipChunk) {
        // TODO: length-prefixed binary encoding.
    }

    override suspend fun close() {
        session.close()
    }

    companion object {
        suspend fun connect(client: HttpClient, host: String, port: Int, path: String = "/ws"): WebSocketTransport {
            val session = client.webSocketSession {
                method = HttpMethod.Get
                url.protocol = io.ktor.http.URLProtocol.WS
                url.host = host
                url.port = port
                url.encodedPath = path
            }
            return WebSocketTransport(session)
        }

        fun httpClient(): HttpClient = HttpClient { install(WebSockets) }
    }
}
