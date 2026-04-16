package dev.uclip.transport

import dev.uclip.protocol.ClipChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClipChunkCodecTest {
    @Test
    fun roundTrip() {
        val chunk = ClipChunk("clip-abc", 7, byteArrayOf(1, 2, 3, 4, 5))
        val decoded = ClipChunkCodec.decode(ClipChunkCodec.encode(chunk))
        assertEquals(chunk, decoded)
    }

    @Test
    fun emptyCiphertext() {
        val chunk = ClipChunk("a", 0, ByteArray(0))
        val decoded = ClipChunkCodec.decode(ClipChunkCodec.encode(chunk))
        assertEquals(chunk, decoded)
    }

    @Test
    fun largeCiphertext() {
        val payload = ByteArray(64 * 1024) { (it and 0xFF).toByte() }
        val chunk = ClipChunk("clip-large", 42, payload)
        val decoded = ClipChunkCodec.decode(ClipChunkCodec.encode(chunk))
        assertEquals(chunk, decoded)
    }

    @Test
    fun rejectsTruncated() {
        val good = ClipChunkCodec.encode(ClipChunk("x", 1, byteArrayOf(9)))
        assertFailsWith<IllegalArgumentException> { ClipChunkCodec.decode(good.copyOf(3)) }
    }
}
