package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class ConnectionStateTransitionsTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    @Test
    fun `state goes Idle to Connecting to Connected on a successful join`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c

        val collector = async {
            c.connectionState.take(3).toList()
        }
        c.joinRoom(roomID = 1, userName = "alice")

        val seen = withTimeout(2000) { collector.await() }
        assertEquals(
            listOf(ConnectionState.Idle, ConnectionState.Connecting, ConnectionState.Connected),
            seen
        )
    }

    @Test
    fun `state moves to Disconnected when server closes unexpectedly`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.first { it == ConnectionState.Connected }

        control.closeCurrent(code = CloseReason.Codes.INTERNAL_ERROR, reason = "boom")

        val disc = withTimeout(2000) {
            c.connectionState.first { it is ConnectionState.Disconnected }
        }
        assertTrue(disc is ConnectionState.Disconnected)
    }
}
