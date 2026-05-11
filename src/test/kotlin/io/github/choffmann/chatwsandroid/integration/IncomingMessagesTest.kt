package io.github.choffmann.chatwsandroid.integration

import io.github.choffmann.chatwsandroid.ChatWsClient
import io.github.choffmann.chatwsandroid.ChatWsConfig
import io.github.choffmann.chatwsandroid.ConnectionState
import io.github.choffmann.chatwsandroid.model.Message
import io.github.choffmann.chatwsandroid.model.MessageType
import io.github.choffmann.chatwsandroid.support.TestServer
import io.github.choffmann.chatwsandroid.support.TestServerExtension
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerExtension::class)
class IncomingMessagesTest {

    private var client: ChatWsClient? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            client?.disconnect()
            client?.close()
        }
    }

    private fun msg(id: String, text: String) = """
        {
          "id": "$id",
          "type": "message",
          "message": "$text",
          "timestamp": "2026-05-11T10:00:00Z",
          "user": { "id": "u-bob", "name": "bob" }
        }
    """.trimIndent()

    @Test
    fun `regular messages are emitted on incomingMessages in order`(control: TestServer.Control) = runBlocking {
        val c = ChatWsClient(ChatWsConfig(baseWsUrl = control.baseWsUrl, enableLogging = false))
        client = c
        c.joinRoom(roomID = 1, userName = "alice")
        control.awaitConnection(1)
        c.connectionState.filter { it == ConnectionState.Connected }.first()

        // Collect 3 messages in a child coroutine that we will join with timeout
        val collected = mutableListOf<Message>()
        val collector = async {
            c.incomingMessages.take(3).toList(collected)
        }

        // Give the collector a moment to subscribe before the server sends
        kotlinx.coroutines.delay(100)

        control.sendText(msg("m1", "one"))
        control.sendText(msg("m2", "two"))
        control.sendText(msg("m3", "three"))

        withTimeout(2000) { collector.await() }

        assertEquals(listOf("m1", "m2", "m3"), collected.map { it.id })
        assertEquals(listOf("one", "two", "three"), collected.map { it.message })
        assertEquals(MessageType.MESSAGE, collected[0].type)
    }
}
