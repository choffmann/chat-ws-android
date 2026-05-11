package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class SendBinaryTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    @Test
    fun `sendBinary delivers a Binary frame with exact bytes`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.filter { it == ConnectionState.Connected }.first()

        val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val ok = c.sendBinary(payload)
        assertTrue(ok)

        withTimeout(1000) {
            while (control.received.isEmpty()) delay(10)
        }

        val frame = control.received.single()
        assertTrue(frame is TestServer.ReceivedFrame.Binary, "expected Binary, got $frame")
        assertArrayEquals(payload, (frame as TestServer.ReceivedFrame.Binary).bytes)
    }

    @Test
    fun `sendBinary returns false when no session is active`() = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = "ws://localhost:1", enableLogging = false))
        client = c
        val ok = c.sendBinary(byteArrayOf(1, 2, 3))
        assertFalse(ok)
    }
}
