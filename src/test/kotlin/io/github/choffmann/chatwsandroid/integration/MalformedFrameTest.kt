package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class MalformedFrameTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    @Test
    fun `malformed JSON does not crash the client and stays Connected`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.first { it == ConnectionState.Connected }

        control.sendText("{ not valid json")

        val maybe = withTimeoutOrNull(500) { c.incomingMessages.first() }
        assertNull(maybe, "incoming flow must not emit for malformed JSON, got: $maybe")

        // State unchanged
        assertEquals(ConnectionState.Connected, c.connectionState.first())
    }
}
