package dev.uclip.transport

import dev.uclip.protocol.ClipChunk
import dev.uclip.protocol.ControlFrame
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EchoTransportTest {

    private val port = freePort()
    private val wsServer = WebSocketServer(port = port).also { it.start() }
    private val httpClient: HttpClient = WebSocketTransport.httpClient()

    @AfterTest
    fun tearDown() {
        wsServer.stop()
        httpClient.close()
    }

    @Test
    fun controlFrameRoundTrip() = runBlocking {
        val acceptedDeferred = async { wsServer.accepted.first() }
        val client = WebSocketTransport.connect(httpClient, "127.0.0.1", port)
        val accepted = acceptedDeferred.await()

        val hello = ControlFrame.Hello(deviceId = "mac-1", displayName = "Mac", pubKey = "pk")
        val received = async {
            withTimeout(3_000) {
                accepted.inbound.filterIsInstance<InboundFrame.Control>().first()
            }
        }
        client.send(hello)
        assertEquals(hello, received.await().frame)
        client.close()
        accepted.close()
    }

    @Test
    fun clipChunkRoundTrip() = runBlocking {
        val acceptedDeferred = async { wsServer.accepted.first() }
        val client = WebSocketTransport.connect(httpClient, "127.0.0.1", port)
        val accepted = acceptedDeferred.await()

        val chunk = ClipChunk("clip-1", 0, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val received = async {
            withTimeout(3_000) {
                accepted.inbound.filterIsInstance<InboundFrame.Data>().first()
            }
        }
        client.send(chunk)
        assertEquals(chunk, received.await().chunk)
        client.close()
        accepted.close()
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
