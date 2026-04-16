package dev.uclip.app.clip

import dev.uclip.crypto.Crypto
import dev.uclip.crypto.NONCE_BYTES
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
 * Each peer carries its own AEAD key (derived via X25519 during pairing), so
 * payloads are sealed individually per-peer.
 */
class ClipSyncController(
    private val bridge: ClipboardBridge,
    private val crypto: Crypto,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val peers = mutableMapOf<String, PeerContext>()
    private val recentLocalWrites = ArrayDeque<String>()

    private val _status = MutableStateFlow(Status(connectedCount = 0, lastEvent = null))
    val status: StateFlow<Status> = _status.asStateFlow()

    data class Status(val connectedCount: Int, val lastEvent: String?)

    private class PeerContext(
        val transport: Transport,
        val sharedKey: ByteArray,
        val consumeJob: Job,
        val inFlight: MutableMap<String, IncomingClip> = mutableMapOf(),
    )

    private class IncomingClip(
        val meta: ControlFrame.ClipMeta,
        val chunks: Array<ByteArray?>,
    )

    fun start() {
        bridge.start()
        scope.launch {
            bridge.changes.collect { content -> onLocalChange(content) }
        }
    }

    suspend fun attach(peerId: String, transport: Transport, sharedKey: ByteArray) {
        mutex.withLock {
            peers.remove(peerId)?.let { it.consumeJob.cancel() }
            val job = scope.launch { consume(peerId, transport) }
            peers[peerId] = PeerContext(transport, sharedKey, job)
            _status.value = _status.value.copy(
                connectedCount = peers.size,
                lastEvent = "Connected $peerId",
            )
        }
    }

    suspend fun detach(peerId: String) {
        mutex.withLock {
            peers.remove(peerId)?.let { it.consumeJob.cancel() }
            _status.value = _status.value.copy(
                connectedCount = peers.size,
                lastEvent = "Disconnected $peerId",
            )
        }
    }

    fun stop() {
        bridge.stop()
        scope.cancel()
        peers.clear()
    }

    private suspend fun onLocalChange(content: ClipboardContent) {
        val text = (content as? ClipboardContent.Text)?.text ?: return
        val hash = crypto.sha256(text.encodeToByteArray()).toHex()
        if (markIfEcho(hash)) return
        val snapshot = mutex.withLock { peers.toMap() }
        if (snapshot.isEmpty()) return
        snapshot.forEach { (_, ctx) -> runCatching { sendText(ctx, text) } }
        _status.value = _status.value.copy(lastEvent = "Sent ${text.truncate()}")
    }

    private suspend fun sendText(ctx: PeerContext, text: String) {
        val bytes = text.encodeToByteArray()
        val clipId = randomClipId()
        val chunks = bytes.chunkedBy(DEFAULT_CHUNK_SIZE)
        ctx.transport.send(
            ControlFrame.ClipMeta(
                clipId = clipId,
                mime = MimeTypes.TEXT_PLAIN,
                bytes = bytes.size.toLong(),
                sha256 = crypto.sha256(bytes).toHex(),
                chunks = chunks.size.coerceAtLeast(1),
            )
        )
        val effective = if (chunks.isEmpty()) listOf(ByteArray(0)) else chunks
        effective.forEachIndexed { i, chunk ->
            val sealed = crypto.seal(ctx.sharedKey, chunk)
            ctx.transport.send(ClipChunk(clipId, i, sealed.encode()))
        }
    }

    private suspend fun consume(peerId: String, transport: Transport) {
        transport.inbound.collect { frame ->
            when (frame) {
                is InboundFrame.Control -> handleControl(peerId, transport, frame.frame)
                is InboundFrame.Data -> handleChunk(peerId, frame.chunk)
            }
        }
    }

    private suspend fun handleControl(peerId: String, transport: Transport, frame: ControlFrame) {
        when (frame) {
            is ControlFrame.ClipMeta -> {
                val ctx = peers[peerId] ?: return
                ctx.inFlight[frame.clipId] = IncomingClip(
                    meta = frame,
                    chunks = arrayOfNulls(frame.chunks.coerceAtLeast(1)),
                )
            }
            is ControlFrame.Hello -> {
                _status.value = _status.value.copy(lastEvent = "Hello from ${frame.displayName}")
            }
            is ControlFrame.Ping -> transport.send(ControlFrame.Pong)
            else -> { /* pairing frames handled in ConnectionManager; others TBD */ }
        }
    }

    private fun handleChunk(peerId: String, chunk: ClipChunk) {
        val ctx = peers[peerId] ?: return
        val inc = ctx.inFlight[chunk.clipId] ?: return
        if (chunk.seq !in inc.chunks.indices) return
        val sealed = decodeSealed(chunk.ciphertext)
        inc.chunks[chunk.seq] = crypto.open(ctx.sharedKey, sealed)
        if (inc.chunks.all { it != null }) {
            ctx.inFlight.remove(chunk.clipId)
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
                else -> { /* future content types */ }
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
}

private fun randomClipId(): String {
    val bytes = ByteArray(8)
    Random.nextBytes(bytes)
    return bytes.toHex()
}

private fun ByteArray.chunkedBy(chunkSize: Int): List<ByteArray> {
    if (isEmpty()) return emptyList()
    val count = (size + chunkSize - 1) / chunkSize
    return List(count) { i ->
        val from = i * chunkSize
        val to = minOf(from + chunkSize, size)
        copyOfRange(from, to)
    }
}

private fun ByteArray.toHex(): String = buildString(size * 2) {
    for (b in this@toHex) append(HEX[(b.toInt() ushr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
}

private const val HEX = "0123456789abcdef"

private fun String.truncate(max: Int = 32): String =
    if (length <= max) this else substring(0, max) + "…"

private fun SealedPayload.encode(): ByteArray = nonce + ciphertext

private fun decodeSealed(raw: ByteArray): SealedPayload {
    require(raw.size >= NONCE_BYTES) { "sealed payload too short" }
    return SealedPayload(raw.copyOfRange(0, NONCE_BYTES), raw.copyOfRange(NONCE_BYTES, raw.size))
}
