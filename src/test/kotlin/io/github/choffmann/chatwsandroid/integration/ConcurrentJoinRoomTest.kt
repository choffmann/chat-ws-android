package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class ConcurrentJoinRoomTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    @Test
    fun `second joinRoom replaces the first session, not stacks it`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c

        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.first { it == ConnectionState.Connected }

        c.joinRoom(roomID = 2, userName = "alice")
        // Allow time for the second connection to open and any duplicates to manifest.
        withTimeout(2000) {
            while (control.accepted.size < 2) delay(20)
        }
        // Settle: give any stray duplicate up to 500ms to surface.
        delay(500)

        assertEquals(2, control.accepted.size, "exactly one connection per joinRoom call")
        assertEquals(listOf(1, 2), control.accepted.map { it.roomId })
    }
}
