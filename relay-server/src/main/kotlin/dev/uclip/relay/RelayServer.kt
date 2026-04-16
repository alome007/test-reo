package dev.uclip.relay

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zero-knowledge rendezvous relay. Two sockets claim the same pairId and
 * bytes are pumped between them verbatim. The server never sees plaintext;
 * payloads are E2E-encrypted before they reach it.
 */
object Rooms {
    private data class Room(var a: WebSocketSession? = null, var b: WebSocketSession? = null)

    private val rooms = ConcurrentHashMap<String, Room>()
    private val lock = Mutex()

    suspend fun join(pairId: String, session: WebSocketSession): WebSocketSession? = lock.withLock {
        val room = rooms.getOrPut(pairId) { Room() }
        when {
            room.a == null -> { room.a = session; null }
            room.b == null -> { room.b = session; room.a }
            else -> { session.close(); null }
        }
    }

    suspend fun leave(pairId: String, session: WebSocketSession) = lock.withLock {
        val room = rooms[pairId] ?: return@withLock
        if (room.a === session) room.a = null
        if (room.b === session) room.b = null
        if (room.a == null && room.b == null) rooms.remove(pairId)
    }

    fun peer(pairId: String, me: WebSocketSession): WebSocketSession? {
        val room = rooms[pairId] ?: return null
        return if (room.a === me) room.b else room.a
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port) {
        install(WebSockets)
        routing {
            webSocket("/rendezvous/{pairId}") {
                val pairId = call.parameters["pairId"] ?: return@webSocket close()
                Rooms.join(pairId, this)
                try {
                    incoming.consumeEach { frame ->
                        val peer = Rooms.peer(pairId, this) ?: return@consumeEach
                        when (frame) {
                            is Frame.Text -> peer.send(frame.readText())
                            is Frame.Binary -> peer.send(Frame.Binary(true, frame.readBytes()))
                            else -> {}
                        }
                    }
                } finally {
                    Rooms.leave(pairId, this)
                }
            }
        }
    }.start(wait = true)
}
