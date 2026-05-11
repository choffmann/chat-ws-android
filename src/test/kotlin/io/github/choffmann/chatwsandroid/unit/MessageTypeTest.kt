package io.github.choffmann.chatwsandroid.unit

import io.github.choffmann.chatwsandroid.model.MessageType
import io.github.choffmann.chatwsandroid.net.AppJson
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageTypeTest {

    @Test
    fun `MESSAGE encodes to "message"`() {
        assertEquals("\"message\"", AppJson.encodeToString(MessageType.MESSAGE))
    }

    @Test
    fun `IMAGE encodes to "image"`() {
        assertEquals("\"image\"", AppJson.encodeToString(MessageType.IMAGE))
    }

    @Test
    fun `FILE encodes to "file"`() {
        assertEquals("\"file\"", AppJson.encodeToString(MessageType.FILE))
    }

    @Test
    fun `SYSTEM encodes to "system"`() {
        assertEquals("\"system\"", AppJson.encodeToString(MessageType.SYSTEM))
    }

    @Test
    fun `custom type round-trips`() {
        val custom = MessageType("reaction")
        val encoded = AppJson.encodeToString(custom)
        assertEquals("\"reaction\"", encoded)
        val decoded = AppJson.decodeFromString<MessageType>(encoded)
        assertEquals(custom, decoded)
    }
}
