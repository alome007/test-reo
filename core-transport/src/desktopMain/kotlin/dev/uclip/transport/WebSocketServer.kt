package dev.uclip.transport

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * JVM-side WebSocket server that accepts incoming peers and exposes each one
 * as a [Transport]. One server per device; peers connect via QR-shared host:port.
 */
class WebSocketServer(
    private val port: Int,
    private val path: String = "/ws",
) {
    private val _accepted = MutableSharedFlow<Transport>(extraBufferCapacity = 16)
    val accepted: Flow<Transport> = _accepted.asSharedFlow()

    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        val s = embeddedServer(CIO, port = port) {
            install(WebSockets)
            routing {
                webSocket(path) {
                    val transport = WebSocketTransport.fromSession(this)
                    _accepted.emit(transport)
                    // Keep the route handler alive until the client disconnects.
                    val done = CompletableDeferred<Unit>()
                    closeReason.invokeOnCompletion { done.complete(Unit) }
                    done.await()
                }
            }
        }
        s.start(wait = false)
        server = s
    }

    fun resolvedPort(): Int = port

    fun stop() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
    }
}
