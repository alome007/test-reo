package dev.uclip.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesTest {
    private val json = Json { classDiscriminator = "type" }

    @Test
    fun helloRoundTrip() {
        val hello: ControlFrame = ControlFrame.Hello(
            deviceId = "device-1",
            displayName = "Mac",
            pubKey = "base64key",
        )
        val encoded = json.encodeToString(ControlFrame.serializer(), hello)
        val decoded = json.decodeFromString(ControlFrame.serializer(), encoded)
        assertEquals(hello, decoded)
    }

    @Test
    fun clipMetaRoundTrip() {
        val meta: ControlFrame = ControlFrame.ClipMeta(
            clipId = "c1",
            mime = MimeTypes.TEXT_PLAIN,
            bytes = 5,
            sha256 = "abc",
            chunks = 1,
        )
        val encoded = json.encodeToString(ControlFrame.serializer(), meta)
        val decoded = json.decodeFromString(ControlFrame.serializer(), encoded)
        assertEquals(meta, decoded)
    }
}
