package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.model.MessageType
import io.github.choffmann.chatwsandroid.model.OutgoingMessage
import io.github.choffmann.chatwsandroid.net.AppJson
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class SendMessageTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    @Test
    fun `sendMessage with default type produces a single MESSAGE text frame`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.filter { it == ConnectionState.Connected }.first()

        val ok = c.sendMessage("hello")
        assertTrue(ok)

        withTimeout(1000) {
            while (control.received.isEmpty()) delay(10)
        }

        val frames = control.received
        assertEquals(1, frames.size)
        val text = (frames.single() as TestServer.ReceivedFrame.Text).text
        val decoded = AppJson.decodeFromString<OutgoingMessage>(text)
        assertEquals(MessageType.MESSAGE, decoded.type)
        assertEquals("hello", decoded.message)
        assertEquals(null, decoded.additionalInfo)
    }

    @Test
    fun `sendMessage with custom type and additionalInfo round-trips`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.filter { it == ConnectionState.Connected }.first()

        val info = buildJsonObject { put("flag", true) }
        val ok = c.sendMessage("ping", type = MessageType("reaction"), additionalInfo = info)
        assertTrue(ok)

        withTimeout(1000) {
            while (control.received.isEmpty()) delay(10)
        }

        val text = (control.received.single() as TestServer.ReceivedFrame.Text).text
        val decoded = AppJson.decodeFromString<OutgoingMessage>(text)
        assertEquals(MessageType("reaction"), decoded.type)
        assertEquals("ping", decoded.message)
        assertEquals(JsonPrimitive(true), decoded.additionalInfo?.get("flag"))
    }

    @Test
    fun `sendMessage returns false when no session is active`() = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = "ws://localhost:1", enableLogging = false))
        client = c
        val ok = c.sendMessage("hello")
        assertFalse(ok)
    }
}
