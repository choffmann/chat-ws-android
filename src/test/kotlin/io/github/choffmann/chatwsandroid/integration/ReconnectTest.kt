package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class ReconnectTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    @Test
    fun `client reconnects after server-initiated close`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.first { it == ConnectionState.Connected }

        control.closeCurrent(code = CloseReason.Codes.INTERNAL_ERROR, reason = "boom")

        // Wait up to 5 s for a second connection (first backoff is ~1 s).
        withTimeout(5000) {
            while (control.accepted.size < 2) delay(50)
        }
        assertEquals(2, control.accepted.size)
    }

    @Test
    fun `disconnect cancels the reconnect loop`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.first { it == ConnectionState.Connected }

        c.disconnect()

        // After disconnect, even if the server tries to provoke a reconnect by closing,
        // no new connection should appear within a reasonable window.
        control.closeCurrent()
        delay(1500)
        assertEquals(1, control.accepted.size, "no new connection should be opened after disconnect()")

        val state = c.connectionState.first()
        assertTrue(state is ConnectionState.Disconnected && state.cause == null)
    }
}
