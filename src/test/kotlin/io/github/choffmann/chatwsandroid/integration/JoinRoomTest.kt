package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class JoinRoomTest {

    private var client: ChatWsClient? = null

    @AfterEach fun tearDown() { runBlocking {
        client?.disconnect()
        client?.close()
    } }

    private fun newClient(control: TestServer.Control): ChatWsClient {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        return c
    }

    @Test
    fun `joinRoom with userName opens ws to room path with userName query`(control: TestServer.Control) = runBlocking {
        withTimeout(2000) {
            val c = newClient(control)
            c.joinRoom(roomID = 42, userName = "alice")
            control.awaitConnection(1)
        }
        val acc = control.accepted.single()
        assertEquals(42, acc.roomId)
        assertEquals("alice", acc.userName)
        assertEquals(null, acc.userId)
        assertEquals(true, acc.userInfo)
    }

    @Test
    fun `joinRoom with userId opens ws with userId query`(control: TestServer.Control) = runBlocking {
        withTimeout(2000) {
            val c = newClient(control)
            c.joinRoom(roomID = 7, userId = "u-123")
            control.awaitConnection(1)
        }
        val acc = control.accepted.single()
        assertEquals(7, acc.roomId)
        assertEquals("u-123", acc.userId)
        assertEquals(true, acc.userInfo)
    }

    @Test
    fun `joinRoom URL-encodes special characters in userName`(control: TestServer.Control) = runBlocking {
        val name = "alice bob+ä"
        withTimeout(2000) {
            val c = newClient(control)
            c.joinRoom(roomID = 1, userName = name)
            control.awaitConnection(1)
        }
        val acc = control.accepted.single()
        // Server-side decoding must yield the original string
        assertEquals(name, acc.userName, "userName must be URL-encoded on the wire and decoded server-side back to the original")
        val rawQuery = acc.rawQuery ?: ""
        // Spaces / non-ASCII MUST be percent-encoded on the wire
        assertTrue(!rawQuery.contains(" "), "raw query contained an unencoded space: $rawQuery")
        assertTrue(!rawQuery.contains("ä"), "raw query contained a literal non-ASCII char: $rawQuery")
    }

    @Test
    fun `joinRoom always sets userInfo to true`(control: TestServer.Control) = runBlocking {
        withTimeout(2000) {
            val c = newClient(control)
            c.joinRoom(roomID = 5, userName = "alice")
            control.awaitConnection(1)
        }
        assertEquals(true, control.accepted.single().userInfo)
    }
}
