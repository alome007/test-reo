package dev.uclip.app.clip

import dev.uclip.crypto.Crypto
import dev.uclip.crypto.SealedPayload
import dev.uclip.protocol.ClipChunk
import dev.uclip.protocol.ControlFrame
import dev.uclip.protocol.DEFAULT_CHUNK_SIZE
import dev.uclip.protocol.MimeTypes
import dev.uclip.transport.InboundFrame
import dev.uclip.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Orchestrates clipboard sync across all currently-attached [Transport]s.
 *
 * Responsibilities:
 *   - When the local clipboard changes, broadcast the content to every peer.
 *   - When a peer sends a clip (ClipMeta + ClipChunk frames), reassemble and
 *     write to the local clipboard.
 *   - Dedupe echoes: if we just wrote content with hash H to the local
 *     clipboard, the ensuing OS change event whose content hashes to H is
 *     ignored so we don't bounce it back.
 */
class ClipSyncController(
    private val bridge: ClipboardBridge,
    private val crypto: Crypto,
    // Placeholder key for phase 5; real per-pair keys land with pairing.
    private val sharedKey: ByteArray = ByteArray(32),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val peers = mutableMapOf<String, Transport>()
    private val peerJobs = mutableMapOf<String, Job>()
    private val inFlight = mutableMapOf<String, IncomingClip>()
    private val recentLocalWrites = ArrayDeque<String>()

    private val _status = MutableStateFlow(Status(connectedCount = 0, lastEvent = null))
    val status: StateFlow<Status> = _status.asStateFlow()

    data class Status(val connectedCount: Int, val lastEvent: String?)

    fun start() {
        bridge.start()
        scope.launch {
            bridge.changes.collect { content -> onLocalChange(content) }
        }
    }

    suspend fun attach(peerId: String, transport: Transport) {
        mutex.withLock {
            peers[peerId]?.let { existing ->
                runCatching { existing.close() }
                peerJobs.remove(peerId)?.cancel()
            }
            peers[peerId] = transport
            peerJobs[peerId] = scope.launch { consume(peerId, transport) }
            _status.value = _status.value.copy(connectedCount = peers.size, lastEvent = "Connected $peerId")
        }
    }

    suspend fun detach(peerId: String) {
        mutex.withLock {
            peers.remove(peerId)?.let { runCatching { it.close() } }
            peerJobs.remove(peerId)?.cancel()
            _status.value = _status.value.copy(connectedCount = peers.size, lastEvent = "Disconnected $peerId")
        }
    }

    fun stop() {
        bridge.stop()
        scope.cancel()
        peers.clear()
        peerJobs.clear()
    }

    private suspend fun onLocalChange(content: ClipboardContent) {
        val text = (content as? ClipboardContent.Text)?.text ?: return
        val hash = crypto.sha256(text.encodeToByteArray()).toHex()
        if (markIfEcho(hash)) return
        val current = mutex.withLock { peers.values.toList() }
        if (current.isEmpty()) return
        current.forEach { transport -> runCatching { sendText(transport, text) } }
        _status.value = _status.value.copy(lastEvent = "Sent ${text.truncate()}")
    }

    private suspend fun sendText(transport: Transport, text: String) {
        val bytes = text.encodeToByteArray()
        val clipId = randomClipId()
        val chunks = bytes.toList().chunked(DEFAULT_CHUNK_SIZE).map { it.toByteArray() }
        transport.send(
            ControlFrame.ClipMeta(
                clipId = clipId,
                mime = MimeTypes.TEXT_PLAIN,
                bytes = bytes.size.toLong(),
                sha256 = crypto.sha256(bytes).toHex(),
                chunks = chunks.size.coerceAtLeast(1),
            )
        )
        if (chunks.isEmpty()) {
            val sealed = crypto.seal(sharedKey, ByteArray(0))
            transport.send(ClipChunk(clipId, 0, sealed.encode()))
        } else {
            chunks.forEachIndexed { i, chunk ->
                val sealed = crypto.seal(sharedKey, chunk)
                transport.send(ClipChunk(clipId, i, sealed.encode()))
            }
        }
    }

    private suspend fun consume(peerId: String, transport: Transport) {
        transport.inbound.collect { frame ->
            when (frame) {
                is InboundFrame.Control -> handleControl(peerId, transport, frame.frame)
                is InboundFrame.Data -> handleChunk(frame.chunk)
            }
        }
    }

    private suspend fun handleControl(peerId: String, transport: Transport, frame: ControlFrame) {
        when (frame) {
            is ControlFrame.ClipMeta -> {
                inFlight[frame.clipId] = IncomingClip(
                    meta = frame,
                    chunks = arrayOfNulls(frame.chunks.coerceAtLeast(1)),
                )
            }
            is ControlFrame.Hello -> {
                _status.value = _status.value.copy(lastEvent = "Hello from ${frame.displayName}")
            }
            is ControlFrame.Ping -> transport.send(ControlFrame.Pong)
            else -> { /* phases 4/7+ handle pairing, ack, reject */ }
        }
    }

    private fun handleChunk(chunk: ClipChunk) {
        val inc = inFlight[chunk.clipId] ?: return
        if (chunk.seq !in inc.chunks.indices) return
        val sealed = SealedPayload.decode(chunk.ciphertext)
        inc.chunks[chunk.seq] = crypto.open(sharedKey, sealed)
        if (inc.chunks.all { it != null }) {
            inFlight.remove(chunk.clipId)
            val totalSize = inc.chunks.sumOf { it!!.size }
            val fullBytes = ByteArray(totalSize)
            var p = 0
            for (c in inc.chunks) {
                val decrypted = c!!
                decrypted.copyInto(fullBytes, p)
                p += decrypted.size
            }
            when (inc.meta.mime) {
                MimeTypes.TEXT_PLAIN -> {
                    val text = fullBytes.decodeToString()
                    val hash = crypto.sha256(text.encodeToByteArray()).toHex()
                    rememberLocalWrite(hash)
                    bridge.write(ClipboardContent.Text(text))
                    _status.value = _status.value.copy(lastEvent = "Received ${text.truncate()}")
                }
                else -> { /* phase 6+ */ }
            }
        }
    }

    private fun markIfEcho(hash: String): Boolean {
        val idx = recentLocalWrites.indexOf(hash)
        if (idx == -1) return false
        recentLocalWrites.removeAt(idx)
        return true
    }

    private fun rememberLocalWrite(hash: String) {
        recentLocalWrites.addLast(hash)
        while (recentLocalWrites.size > 8) recentLocalWrites.removeFirst()
    }

    private class IncomingClip(
        val meta: ControlFrame.ClipMeta,
        val chunks: Array<ByteArray?>,
    )
}

private fun randomClipId(): String {
    val bytes = ByteArray(8)
    Random.nextBytes(bytes)
    return bytes.toHex()
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (b in this@toHex) append(HEX[(b.toInt() ushr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
}

private const val HEX = "0123456789abcdef"

private fun String.truncate(max: Int = 32): String =
    if (length <= max) this else substring(0, max) + "…"

private fun SealedPayload.encode(): ByteArray = nonce + ciphertext

private fun SealedPayload.Companion.decode(raw: ByteArray): SealedPayload {
    require(raw.size >= 12) { "sealed payload too short" }
    return SealedPayload(raw.copyOfRange(0, 12), raw.copyOfRange(12, raw.size))
}
