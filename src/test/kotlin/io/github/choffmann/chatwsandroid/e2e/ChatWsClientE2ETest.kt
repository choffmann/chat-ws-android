package io.github.choffmann.chatwsandroid.e2e

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.model.MessageType
import io.github.choffmann.chatwsandroid.support.ServerContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@Tag("e2e")
class ChatWsClientE2ETest {

    companion object {
        @Container
        @JvmStatic
        val server = ServerContainer()

        /** Server-assigned room IDs created before any test runs. */
        var binaryRoomId: Int = -1
        var textRoomId: Int = -1

        @BeforeAll
        @JvmStatic
        fun setupRooms() {
            binaryRoomId = server.createRoom("e2e-binary")
            textRoomId = server.createRoom("e2e-text")
        }
    }

    private val clients = mutableListOf<ChatWsClient>()

    @AfterEach
    fun tearDown() {
        runBlocking {
            clients.forEach { it.disconnect(); it.close() }
            clients.clear()
        }
    }

    private fun newClient(): ChatWsClient {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = server.baseWsUrl(), enableLogging = false))
        clients.add(c)
        return c
    }

    @Test
    fun `two clients in the same room exchange a text message`() = runBlocking {
        val a = newClient()
        val b = newClient()

        a.joinRoom(roomID = textRoomId, userName = "alice")
        b.joinRoom(roomID = textRoomId, userName = "bob")
        a.connectionState.filter { it == ConnectionState.Connected }.first()
        b.connectionState.filter { it == ConnectionState.Connected }.first()

        // Allow the server to broadcast presence
        delay(200)

        val received = async {
            b.incomingMessages.filter { it.user.name == "alice" && it.message == "hello-e2e" }.first()
        }

        a.sendMessage("hello-e2e")

        val msg = withTimeout(5000) { received.await() }
        assertEquals(MessageType.MESSAGE, msg.type)
        assertEquals("hello-e2e", msg.message)
    }

    @Test
    fun `binary upload from A becomes an image message on B with a URL`() = runBlocking {
        val a = newClient()
        val b = newClient()

        a.joinRoom(roomID = binaryRoomId, userName = "alice")
        b.joinRoom(roomID = binaryRoomId, userName = "bob")
        a.connectionState.filter { it == ConnectionState.Connected }.first()
        b.connectionState.filter { it == ConnectionState.Connected }.first()
        delay(200)

        // Minimal valid 1x1 PNG.
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1,
            8, 6, 0, 0, 0, 0x1F.toByte(), 0x15, 0xC4.toByte(), 0x89.toByte(),
            0, 0, 0, 13, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D.toByte(), 0x0A, 0x2D, 0xB4.toByte(),
            0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )

        val received = async {
            b.incomingMessages.filter { it.type == MessageType.IMAGE }.first()
        }

        a.sendBinary(png)

        val msg = withTimeout(10000) { received.await() }
        assertEquals(MessageType.IMAGE, msg.type)
        val url = msg.additionalInfo?.get("url")?.toString()?.trim('"')
        assertTrue(!url.isNullOrEmpty(), "image message should carry a URL in additionalInfo, got: ${msg.additionalInfo}")
    }
}
