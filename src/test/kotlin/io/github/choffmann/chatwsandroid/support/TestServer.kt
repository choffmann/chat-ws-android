package io.github.choffmann.chatwsandroid.support

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.queryString
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Embedded WebSocket server that mirrors the documented contract of the
 * chat-room server. Tests interact with it via [TestServer.Control].
 */
class TestServer {

    /** Records of every connection the server accepted, in order. */
    data class AcceptedConnection(
        val roomId: Int,
        val userName: String?,
        val userId: String?,
        val userInfo: Boolean,
        val rawQuery: String?
    )

    /** Records of every frame received from the connected client. */
    sealed interface ReceivedFrame {
        data class Text(val text: String) : ReceivedFrame
        data class Binary(val bytes: ByteArray) : ReceivedFrame
    }

    /** Control surface for tests. */
    interface Control {
        val port: Int
        val baseWsUrl: String
        val accepted: List<AcceptedConnection>
        val received: List<ReceivedFrame>

        /** Suspends until the n-th connection (1-indexed) has been opened. */
        suspend fun awaitConnection(index: Int = 1)

        /** Send a raw text frame to the currently-connected client. */
        suspend fun sendText(text: String)

        /** Send a raw binary frame to the currently-connected client. */
        suspend fun sendBinary(bytes: ByteArray)

        /** Close the current client connection with the given code/reason. */
        suspend fun closeCurrent(code: CloseReason.Codes = CloseReason.Codes.INTERNAL_ERROR, reason: String = "test")
    }

    private val acceptedList = ConcurrentLinkedQueue<AcceptedConnection>()
    private val receivedList = ConcurrentLinkedQueue<ReceivedFrame>()
    private val connectionGate = Channel<Unit>(capacity = Channel.UNLIMITED)
    @Volatile private var sessionRef: DefaultWebSocketSession? = null

    private val engine = embeddedServer(CIO, port = 0) {
        install(WebSockets)
        routing {
            webSocket("/join/{roomID}") {
                val roomId = call.parameters["roomID"]!!.toInt()
                val q = call.request.queryParameters
                acceptedList.add(
                    AcceptedConnection(
                        roomId = roomId,
                        userName = q["userName"],
                        userId = q["userId"],
                        userInfo = q["userInfo"] == "true",
                        rawQuery = call.request.queryString().ifEmpty { null }
                    )
                )
                connectionGate.send(Unit)
                sessionRef = this

                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> receivedList.add(ReceivedFrame.Text(frame.readText()))
                            is Frame.Binary -> receivedList.add(ReceivedFrame.Binary(frame.data))
                            else -> Unit
                        }
                    }
                } finally {
                    sessionRef = null
                }
            }
        }
    }

    val control: Control = object : Control {
        override val port: Int get() = resolvedPort()
        override val baseWsUrl: String get() = "ws://localhost:${resolvedPort()}"
        override val accepted: List<AcceptedConnection> get() = acceptedList.toList()
        override val received: List<ReceivedFrame> get() = receivedList.toList()

        override suspend fun awaitConnection(index: Int) {
            while (acceptedList.size < index) {
                connectionGate.receive()
            }
        }

        override suspend fun sendText(text: String) {
            sessionRef?.send(Frame.Text(text))
        }

        override suspend fun sendBinary(bytes: ByteArray) {
            sessionRef?.send(Frame.Binary(true, bytes))
        }

        override suspend fun closeCurrent(code: CloseReason.Codes, reason: String) {
            sessionRef?.close(CloseReason(code, reason))
        }
    }

    fun start() {
        engine.start(wait = false)
    }

    fun stop() {
        engine.stop(gracePeriodMillis = 100, timeoutMillis = 500)
    }

    private fun resolvedPort(): Int =
        runBlocking { engine.engine.resolvedConnectors().first().port }
}
