package io.github.choffmann.chatwsandroid

import io.github.choffmann.chatwsandroid.model.*
import io.github.choffmann.chatwsandroid.net.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the websocket client used by [ChatWsClient].
 *
 * @property baseWsUrl Base URL of the chat websocket backend.
 * @property enableLogging When `true`, enables Ktor's verbose logging for easier debugging.
 */
data class ChatWsConfig(
    val baseWsUrl: String = "wss://chat.homebin.dev",
    val enableLogging: Boolean = true
)

/**
 * Websocket client for connecting to the ChatWS backend via Ktor.
 * Performs automatic reconnection with exponential backoff and exposes state via Kotlin flows.
 *
 * Example usage inside an Android `ViewModel`:
 * ```kotlin
 * class ChatViewModel(
 *     private val client: ChatWsClient = ChatWsClient()
 * ) : ViewModel() {
 *
 *     private val _messages = MutableStateFlow<List<Message>>(emptyList())
 *     val messages: StateFlow<List<Message>> = _messages.asStateFlow()
 *
 *     init {
 *         client.incomingMessages
 *             .onEach { incoming -> _messages.update { it + incoming } }
 *             .launchIn(viewModelScope)
 *     }
 *
 *     fun connect(roomId: Int, userName: String) {
 *         client.joinRoom(roomId, userName)
 *     }
 *
 *     fun sendMessage(text: String) {
 *         viewModelScope.launch {
 *             client.sendMessage(text)
 *         }
 *     }
 *
 *     override fun onCleared() {
 *         viewModelScope.launch { client.disconnect() }
 *         super.onCleared()
 *     }
 * }
 * ```
 */
class ChatWsClient(
    private val config: ChatWsConfig = ChatWsConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

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

    /**
     * Stream of domain [Message] instances received from the websocket connection.
     */
    val incomingMessages: Flow<Message> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

    /**
     * Stream that reports the current [ConnectionState] of the client.
     */
    val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)

    /**
     * Stream that exposes the current user information (ID and name).
     * This is set when the client successfully joins a room and receives the welcome message from the server.
     * The user info includes the server-assigned or provided username.
     */
    val currentUser: Flow<User?> = _currentUser.asStateFlow()

    /**
     * Opens the websocket connection and starts a background reader coroutine.
     *
     * @param roomID Identifier of the room to join.
     * @param userName Optional username for creating a new ephemeral user (ignored if userId is provided).
     * @param userId Optional user ID to retrieve and use an existing registered user from the server.
     *
     * **Important:** If `userId` is provided, it takes precedence and `userName` is ignored.
     * The server will look up the registered user and use their stored name/properties.
     *
     * Example usage from a `ViewModel`:
     * ```kotlin
     * // Join with username only - creates new ephemeral user
     * fun connectWithName(roomId: Int, userName: String) {
     *     client.joinRoom(roomId, userName = userName)
     * }
     *
     * // Join with userId only - uses existing registered user
     * fun connectWithId(roomId: Int, userId: String) {
     *     client.joinRoom(roomId, userId = userId)
     * }
     *
     * // Join anonymously - server assigns random name
     * fun connectAnonymously(roomId: Int) {
     *     client.joinRoom(roomId)
     * }
     *
     * // Note: If both userId and userName are provided, only userId is used
     * ```
     */
    fun joinRoom(roomID: Int, userName: String? = null, userId: String? = null) {
        scope.launch {
            _connectionState.emit(ConnectionState.Connecting)
            var attempt = 0
            while (session == null && isActive) {
                try {
                    val wsUrl = buildString {
                        append("${config.baseWsUrl}/join/$roomID")
                        val params = mutableListOf<String>()
                        if (!userName.isNullOrEmpty()) params.add("user=$userName")
                        if (!userId.isNullOrEmpty()) params.add("userId=$userId")
                        params.add("userInfo=true")
                        append("?${params.joinToString("&")}")
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
                                .onSuccess { message ->
                                    val info = message.additionalInfo
                                    val isSelf = info?.get("self")?.jsonPrimitive?.booleanOrNull == true
                                    val joinedUserId = info?.get("joinedUserId")?.jsonPrimitive?.contentOrNull
                                    val joinedUserName = info?.get("joinedUserName")?.jsonPrimitive?.contentOrNull

                                    if (isSelf && joinedUserId != null && joinedUserName != null) {
                                        _currentUser.emit(User(id = joinedUserId, name = joinedUserName))
                                    } else {
                                        _incomingMessages.tryEmit(message)
                                    }
                                }
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
     * Sends a text message to the room.
     *
     * @param message UTF-8 text payload.
     * @param type Message type — defaults to [MessageType.MESSAGE]. Use any custom string via `MessageType("yourType")`.
     * @param additionalInfo Optional metadata that will be broadcast to all participants.
     * @return `true` if the frame was sent, `false` when no active connection is available or sending failed.
     */
    suspend fun sendMessage(
        message: String,
        type: MessageType = MessageType.MESSAGE,
        additionalInfo: JsonObject? = null
    ): Boolean {
        val s = session ?: return false
        return try {
            val outgoingMessage = OutgoingMessage(
                type = type,
                message = message,
                additionalInfo = additionalInfo
            )
            val json = AppJson.encodeToString(outgoingMessage)
            s.send(Frame.Text(json))
            true
        } catch (t: Throwable) {
            _connectionState.emit(ConnectionState.Disconnected(t))
            false
        }
    }

    /**
     * Sends raw binary data as a WebSocket binary frame.
     * The server detects the MIME type, saves the file, and broadcasts a message
     * with type "image" or "file" containing the download URL.
     *
     * @param data Raw bytes of the file to upload (max 5 MiB).
     * @return `true` if the frame was sent, `false` when no active connection is available or sending failed.
     */
    suspend fun sendBinary(data: ByteArray): Boolean {
        val s = session ?: return false
        return try {
            s.send(Frame.Binary(true, data))
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
     *     viewModelScope.launch { client.disconnect() }
     *     super.onCleared()
     * }
     * ```
     */
    suspend fun disconnect() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "User closed"))
        closeInternal()
        _connectionState.emit(ConnectionState.Disconnected(null))
        _currentUser.emit(null)
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
