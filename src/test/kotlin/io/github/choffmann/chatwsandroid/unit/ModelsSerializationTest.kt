package io.github.choffmann.chatwsandroid.unit

import io.github.choffmann.chatwsandroid.model.Message
import io.github.choffmann.chatwsandroid.model.MessageType
import io.github.choffmann.chatwsandroid.model.OutgoingMessage
import io.github.choffmann.chatwsandroid.model.User
import io.github.choffmann.chatwsandroid.net.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelsSerializationTest {

    @Test
    fun `Message decodes a fully populated server payload`() {
        val json = """
            {
              "id": "m1",
              "type": "message",
              "message": "hello",
              "timestamp": "2026-05-11T10:00:00Z",
              "user": { "id": "u1", "name": "alice" },
              "additionalInfo": { "k": "v" }
            }
        """.trimIndent()

        val msg = AppJson.decodeFromString<Message>(json)

        assertEquals("m1", msg.id)
        assertEquals(MessageType.MESSAGE, msg.type)
        assertEquals("hello", msg.message)
        assertEquals(User(id = "u1", name = "alice"), msg.user)
        assertEquals(JsonPrimitive("v"), msg.additionalInfo?.get("k"))
    }

    @Test
    fun `Message decode ignores unknown fields`() {
        val json = """
            {
              "id": "m1",
              "type": "message",
              "message": "hello",
              "timestamp": "2026-05-11T10:00:00Z",
              "user": { "id": "u1", "name": "alice" },
              "futureField": 42
            }
        """.trimIndent()

        val msg = AppJson.decodeFromString<Message>(json)
        assertEquals("m1", msg.id)
    }

    @Test
    fun `OutgoingMessage encodes type, message, and additionalInfo and omits nulls`() {
        val out = OutgoingMessage(
            type = MessageType.MESSAGE,
            message = "hi",
            additionalInfo = buildJsonObject { put("flag", true) }
        )

        val encoded = AppJson.encodeToString(out)

        assertTrue(encoded.contains("\"type\":\"message\""))
        assertTrue(encoded.contains("\"message\":\"hi\""))
        assertTrue(encoded.contains("\"flag\":true"))
    }

    @Test
    fun `OutgoingMessage omits additionalInfo when null`() {
        val out = OutgoingMessage(type = MessageType.MESSAGE, message = "hi", additionalInfo = null)
        val encoded = AppJson.encodeToString(out)
        assertTrue(!encoded.contains("additionalInfo"), "encoded was: $encoded")
    }

    @Test
    fun `User decodes minimal payload with only id and name`() {
        val json = """{ "id": "u1", "name": "alice" }"""
        val user = AppJson.decodeFromString<User>(json)
        assertEquals("u1", user.id)
        assertEquals("alice", user.name)
        assertNull(user.firstName)
        assertNull(user.lastName)
        assertNull(user.additionalInfo)
    }

    @Test
    fun `User decodes full payload with firstName, lastName, and additionalInfo`() {
        val json = """
            {
              "id": "u1",
              "name": "alice",
              "firstName": "Alice",
              "lastName": "Anderson",
              "additionalInfo": { "role": "admin" }
            }
        """.trimIndent()
        val user = AppJson.decodeFromString<User>(json)
        assertEquals("Alice", user.firstName)
        assertEquals("Anderson", user.lastName)
        assertEquals(JsonPrimitive("admin"), user.additionalInfo?.get("role"))
    }
}
