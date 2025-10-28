package io.github.choffmann.chatwsandroid.impl

import io.github.choffmann.chatwsandroid.api.*
import io.github.choffmann.chatwsandroid.model.*
import io.github.choffmann.chatwsandroid.net.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the websocket client used by [ChatWsMessageRepository].
 *
 * @property baseWsUrl Base URL of the chat websocket backend.
 * @property enableLogging When `true`, enables Ktor's verbose logging for easier debugging.
 */
data class ChatWsConfig(
    val baseWsUrl: String = "wss://chat.homebin.dev",
    val enableLogging: Boolean = true
)

/**
 * [MessageRepository] implementation that targets the ChatWS backend via Ktor's websocket client.
 * It performs reconnection with exponential backoff and exposes state via Kotlin flows.
 *
 * Example usage inside an Android `ViewModel`:
 * ```kotlin
 * class ChatViewModel(
 *     private val repository: MessageRepository = ChatWsMessageRepository()
 * ) : ViewModel() {
 *
 *     private val _messages = MutableStateFlow<List<Message>>(emptyList())
 *     val messages: StateFlow<List<Message>> = _messages.asStateFlow()
 *
 *     init {
 *         repository.incomingMessages
 *             .onEach { incoming -> _messages.update { it + incoming } }
 *             .launchIn(viewModelScope)
 *     }
 *
 *     fun connect(roomId: Int, userName: String) {
 *         repository.joinRoom(roomId, userName)
 *     }
 *
 *     fun sendMessage(text: String) {
 *         viewModelScope.launch {
 *             repository.sendMessage(text)
 *         }
 *     }
 *
 *     override fun onCleared() {
 *         viewModelScope.launch { repository.disconnect() }
 *         super.onCleared()
 *     }
 * }
 * ```
 */
class ChatWsMessageRepository(
    private val config: ChatWsConfig = ChatWsConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MessageRepository {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(AppJson) }
        if (config.enableLogging) install(Logging) { level = LogLevel.ALL }
        install(WebSockets)
    }

    private var session: WebSocketSession? = null
    private var readerJob: Job? = null

    private val _incomingMessages = MutableSharedFlow<Message>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingMessages: Flow<Message> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Opens the websocket connection and starts a background reader coroutine.
     *
     * Example usage from a `ViewModel`:
     * ```kotlin
     * fun connect(roomId: Int, userName: String) {
     *     repository.joinRoom(roomId, userName)
     * }
     * ```
     */
    override fun joinRoom(roomID: Int, userName: String?) {
        scope.launch {
            _connectionState.emit(ConnectionState.Connecting)
            var attempt = 0
            while (session == null && isActive) {
                try {
                    val wsUrl = buildString {
                        append("${config.baseWsUrl}/join/$roomID")
                        if (!userName.isNullOrEmpty()) append("?user=$userName")
                    }
                    session = client.webSocketSession(urlString = wsUrl)
                    _connectionState.emit(ConnectionState.Connected)
                    startReader()
                    break
                } catch (t: Throwable) {
                    attempt++
                    _connectionState.emit(ConnectionState.Disconnected(t))
                    val backoff = (1 shl (attempt - 1)).coerceAtMost(10)
                    delay(backoff.seconds)
                }
            }
        }
    }

    /**
     * Launches a coroutine that continuously reads frames from the websocket and forwards messages.
     */
    private fun startReader() {
        readerJob?.cancel()
        val s = session ?: return
        readerJob = scope.launch {
            try {
                for (frame in s.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            runCatching { AppJson.decodeFromString<Message>(text) }
                                .onSuccess { _incomingMessages.tryEmit(it) }
                                .onFailure { /* Optional: eigenes Error-Flow */ }
                        }
                        is Frame.Close -> {
                            _connectionState.emit(ConnectionState.Disconnected(null))
                            break
                        }
                        else -> Unit
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                _connectionState.emit(ConnectionState.Disconnected(e))
            } catch (e: Throwable) {
                _connectionState.emit(ConnectionState.Disconnected(e))
            } finally {
                closeInternal()
            }
        }
    }

    /**
     * Attempts to send the supplied message frame to the remote peer.
     *
     * @return `true` if the frame was sent, `false` when no active connection is available or sending failed.
     *
     * Example usage from a `ViewModel`:
     * ```kotlin
     * fun sendMessage(text: String) {
     *     viewModelScope.launch {
     *         repository.sendMessage(text)
     *     }
     * }
     * ```
     */
    override suspend fun sendMessage(message: String): Boolean {
        val s = session ?: return false
        return try {
            s.send(Frame.Text(message))
            true
        } catch (t: Throwable) {
            _connectionState.emit(ConnectionState.Disconnected(t))
            false
        }
    }

    /**
     * Gracefully terminates the websocket session and resets the internal state flows.
     *
     * Example usage from a `ViewModel`:
     * ```kotlin
     * override fun onCleared() {
     *     viewModelScope.launch { repository.disconnect() }
     *     super.onCleared()
     * }
     * ```
     */
    override suspend fun disconnect() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "User closed"))
        closeInternal()
        _connectionState.emit(ConnectionState.Disconnected(null))
    }

    /**
     * Cancels the reader job and clears the current session reference.
     */
    private fun closeInternal() {
        readerJob?.cancel()
        readerJob = null
        session = null
    }
}
