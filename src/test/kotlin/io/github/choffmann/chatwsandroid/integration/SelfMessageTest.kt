package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.model.User
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class SelfMessageTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    private val selfPayload = """
        {
          "id": "sys-1",
          "type": "system",
          "message": "joined",
          "timestamp": "2026-05-11T10:00:00Z",
          "user": { "id": "u-self", "name": "alice" },
          "additionalInfo": {
            "self": true,
            "joinedUser": { "id": "u-self", "name": "alice" }
          }
        }
    """.trimIndent()

    @Test
    fun `self message populates currentUser`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.filter { it == ConnectionState.Connected }.first()

        control.sendText(selfPayload)

        val user = withTimeout(2000) { c.currentUser.filterNotNull().first() }
        assertEquals(User(id = "u-self", name = "alice"), user)
    }

    @Test
    fun `self message is NOT emitted on incomingMessages`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.filter { it == ConnectionState.Connected }.first()

        control.sendText(selfPayload)

        val maybe = withTimeoutOrNull(500) { c.incomingMessages.first() }
        assertNull(maybe, "self message must not appear in incomingMessages, got: $maybe")
    }
}
