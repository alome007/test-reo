package dev.uclip.transport

import dev.uclip.protocol.ClipChunk
import dev.uclip.protocol.ControlFrame
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.consumeEach

/**
 * Bidirectional WebSocket transport. Wrapping a `WebSocketSession` (either a
 * client connection or a server-accepted session), it pumps incoming frames
 * into [inbound] and exposes typed [send] for control + binary chunks.
 *
 * Lifetime is owned by [scope]; [close] cancels the consumer and the
 * underlying session.
 */
class WebSocketTransport internal constructor(
    private val session: WebSocketSession,
    parentJob: kotlinx.coroutines.Job? = null,
) : Transport {
    private val scope = CoroutineScope(SupervisorJob(parentJob) + Dispatchers.Default)
    private val _inbound = MutableSharedFlow<InboundFrame>(extraBufferCapacity = 64)
    override val inbound: Flow<InboundFrame> = _inbound.asSharedFlow()

    init {
        scope.launch { consumeLoop() }
    }

    private suspend fun consumeLoop() {
        try {
            session.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val ctrl = ProtocolJson.decodeFromString(ControlFrame.serializer(), frame.readText())
                        _inbound.emit(InboundFrame.Control(ctrl))
                    }
                    is Frame.Binary -> {
                        val chunk = ClipChunkCodec.decode(frame.readBytes())
                        _inbound.emit(InboundFrame.Data(chunk))
                    }
                    else -> { /* ping/pong/close handled by ktor */ }
                }
            }
        } catch (_: Throwable) {
            // session ended; consumers observe via inbound completion
        }
    }

    override suspend fun send(frame: ControlFrame) {
        session.send(ProtocolJson.encodeToString(ControlFrame.serializer(), frame))
    }

    override suspend fun send(chunk: ClipChunk) {
        session.send(Frame.Binary(true, ClipChunkCodec.encode(chunk)))
    }

    override suspend fun close() {
        try { session.close() } catch (_: Throwable) {}
        scope.cancel()
    }

    companion object {
        fun fromSession(session: WebSocketSession): WebSocketTransport = WebSocketTransport(session)

        suspend fun connect(
            client: HttpClient,
            host: String,
            port: Int,
            path: String = "/ws",
        ): WebSocketTransport {
            val session = client.webSocketSession {
                method = HttpMethod.Get
                url.protocol = URLProtocol.WS
                url.host = host
                url.port = port
                url.encodedPath = path
            }
            return WebSocketTransport(session)
        }

        fun httpClient(): HttpClient = HttpClient { install(WebSockets) }
    }
}
