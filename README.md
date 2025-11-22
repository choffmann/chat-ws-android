# Chat WS Android

Lightweight Kotlin library that connects Android clients to the [Chat websocket backend](https://github.com/choffmann/chat-room). It provides a simple `ChatWsClient` for websocket communication, along with serializable models that match the server payloads.

## Gradle Setup

```kotlin
// build.gradle.kts of the consuming module
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.choffmann:chat-ws-android:0.2.0")
}
```

Snapshots (if published) are available from Sonatype:

```kotlin
maven {
    name = "Central Portal Snapshots"
    url = uri("https://central.sonatype.com/repository/maven-snapshots/")

    // Only search this repository for the specific dependency
    content {
        includeModule("io.github.choffmann", "chat-ws-android")
    }
}
```

## Quick Start

Create your own repository interface and implementation:

```kotlin
// Your repository interface
interface ChatRepository {
    val messages: Flow<Message>
    val connectionState: Flow<ConnectionState>

    fun connect(roomId: Int, userName: String? = null, userId: String? = null)
    suspend fun sendMessage(text: String): Boolean
    suspend fun disconnect()
}

// Your implementation using ChatWsClient
class ChatRepositoryImpl(
    private val client: ChatWsClient = ChatWsClient()
) : ChatRepository {
    override val messages: Flow<Message> = client.incomingMessages
    override val connectionState: Flow<ConnectionState> = client.connectionState

    override fun connect(roomId: Int, userName: String?, userId: String?) {
        client.joinRoom(roomId, userName, userId)
    }

    override suspend fun sendMessage(text: String): Boolean {
        return client.sendMessage(text)
    }

    override suspend fun disconnect() {
        client.disconnect()
    }
}

// Use your repository in the ViewModel
class ChatViewModel(
    private val repository: ChatRepository = ChatRepositoryImpl()
) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        repository.messages
            .onEach { incoming -> _messages.update { it + incoming } }
            .launchIn(viewModelScope)
    }

    // Connect with username only - creates new ephemeral user
    fun connectWithName(roomId: Int, userName: String) {
        repository.connect(roomId, userName = userName)
    }

    // Connect with userId only - uses existing registered user
    fun connectWithId(roomId: Int, userId: String) {
        repository.connect(roomId, userId = userId)
    }

    // Connect anonymously - server assigns random name
    fun connectAnonymously(roomId: Int) {
        repository.connect(roomId)
    }

    // Note: If both userId and userName are provided, only userId is used
    // and userName is ignored by the server

    fun send(text: String) = viewModelScope.launch { repository.sendMessage(text) }

    override fun onCleared() {
        viewModelScope.launch { repository.disconnect() }
        super.onCleared()
    }
}
```

### Joining Rooms: userName vs userId

The library supports three ways to join a room:

1. **With `userName` only**: Creates a new ephemeral user with the specified name
   ```kotlin
   client.joinRoom(roomId = 1, userName = "Alice")
   ```
   - Server creates a new user with a fresh UUID
   - User exists only for this session

2. **With `userId` only**: Uses an existing registered user from the server
   ```kotlin
   client.joinRoom(roomId = 1, userId = "existing-uuid-here")
   ```
   - Server looks up the user in its registry
   - Uses stored name and properties
   - Returns 404 if user not found

3. **Anonymous**: Let the server assign a random name
   ```kotlin
   client.joinRoom(roomId = 1)
   ```
   - Server picks a random humorous name (e.g., "Kotlin Kevin", "Gradle Gero")

**Important:** If you provide both `userName` and `userId`, only `userId` is used and `userName` is ignored. The `userId` parameter always takes precedence.

### Configuration

Pass a custom `ChatWsConfig` if you need to point to a self-hosted backend:

```kotlin
val client = ChatWsClient(
    config = ChatWsConfig(
        baseWsUrl = "wss://my-chat.example.com",
        enableLogging = false
    )
)
```

### Connection State

Subscribe to `client.connectionState` (or expose it through your repository) to reflect websocket status in your UI:

```kotlin
client.connectionState.collectLatest { state ->
    when (state) {
        is ConnectionState.Idle -> // Not connected
        is ConnectionState.Connecting -> // Connection in progress
        is ConnectionState.Connected -> // Ready to send messages
        is ConnectionState.Disconnected -> // Connection lost, check state.cause
    }
}
```

`ConnectionState.Disconnected` exposes an optional `cause` you can surface for diagnostics or retry logic.

## Message Format

The library uses a structured JSON format for all outgoing messages:

```json
{
  "type": "message",
  "message": "Hello World",
  "additionalInfo": {
    "key": "value"
  }
}
```

**Fields:**

- `type`: Message type as a string. Standard types include `"message"` (text), `"image"` (Base64-encoded image data), `"system"` (system messages), or any custom event type
- `message`: The actual content (text string or Base64 image data)
- `additionalInfo` (optional): Key-value pairs for custom metadata that will be broadcast to all participants

**Standard Message Types:**

The library provides constants in `MessageTypes` for commonly used types:

- `MessageTypes.MESSAGE` = `"message"` - Regular text messages
- `MessageTypes.IMAGE` = `"image"` - Image messages (Base64-encoded)
- `MessageTypes.SYSTEM` = `"system"` - System notifications
- `MessageTypes.MESSAGE_UPDATED` = `"message_updated"` - Message edit notifications (ephemeral)
- `MessageTypes.MESSAGE_DELETED` = `"message_deleted"` - Message deletion notifications (ephemeral)
- `MessageTypes.USER_TYPING` = `"user_typing"` - Typing indicators (ephemeral)
- `MessageTypes.USER_STOPPED_TYPING` = `"user_stopped_typing"` - Stopped typing (ephemeral)

You can also use any custom string as a message type for application-specific events.

### Sending Text Messages

```kotlin
// Simple text message
client.sendMessage("Hello World")

// Message with additional metadata
client.sendMessage(
    message = "Hello World",
    additionalInfo = mapOf(
        "language" to "en",
        "priority" to "high"
    )
)
```

### Sending Images

Images are Base64-encoded and sent with `type: "image"`. The MIME type is automatically included in `additionalInfo`.

```kotlin
// Simple image send
client.sendImage(imageBytes, "image/jpeg")

// Image with additional metadata
client.sendImage(
    imageData = imageBytes,
    mimeType = "image/png",
    additionalInfo = mapOf(
        "caption" to "My vacation photo",
        "location" to "Berlin"
    )
)
```

The sent JSON will look like:

```json
{
  "type": "image",
  "message": "iVBORw0KGgoAAAANSUhEUgAA...",
  "additionalInfo": {
    "mimeType": "image/png",
    "caption": "My vacation photo",
    "location": "Berlin"
  }
}
```

**Example in your repository:**

```kotlin
interface ChatRepository {
    suspend fun sendImage(imageData: ByteArray, mimeType: String): Boolean
}

class ChatRepositoryImpl(
    private val client: ChatWsClient = ChatWsClient()
) : ChatRepository {
    override suspend fun sendImage(imageData: ByteArray, mimeType: String): Boolean {
        return client.sendImage(imageData, mimeType)
    }
}
```

### Receiving Images

Incoming messages with `type = MessageTypes.IMAGE` contain Base64-encoded image data in the `message` field. Additional metadata like MIME type is available in `additionalInfo`:

```kotlin
client.incomingMessages
    .onEach { message ->
        when (message.type) {
            MessageTypes.MESSAGE -> {
                // Handle text message
                println("Text: ${message.message}")
            }
            MessageTypes.IMAGE -> {
                // Handle image message - message field contains Base64 data
                val imageBytes = Base64.decode(message.message, Base64.DEFAULT)
                val mimeType = message.additionalInfo?.get("mimeType") ?: "image/jpeg"
                // Display or save the image
            }
            MessageTypes.SYSTEM -> {
                // Handle system message
                println("System: ${message.message}")
            }
            else -> {
                // Handle custom event types
                println("Custom event: ${message.type}")
            }
        }
    }
    .launchIn(viewModelScope)
```

## Generic Event System

### Overview

Version 0.2.0 introduces a flexible event system that allows you to send and receive any custom event type. The server broadcasts all events to connected clients in real-time, but only stores persistent message types (`system`, `message`, `image`) in the message history. Ephemeral events like typing indicators are broadcast but not persisted.

### Convenience Flow Properties

The library provides filtered Flow properties for common message types:

```kotlin
class ChatViewModel(
    private val repository: ChatRepository = ChatRepositoryImpl()
) : ViewModel() {

    // Only text messages
    val textMessages = client.textMessages
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Only system notifications
    val systemMessages = client.systemMessages
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Only image messages
    val imageMessages = client.imageMessages
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Message update notifications (when messages are edited)
    val messageUpdates = client.messageUpdates
        .onEach { updatedMessage ->
            // Update your local message list with the edited message
            _messages.update { messages ->
                messages.map { if (it.id == updatedMessage.id) updatedMessage else it }
            }
        }
        .launchIn(viewModelScope)

    // Typing indicators
    val typingUsers = client.typingIndicators
        .map { it.user.name }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Filter any custom event type
    val reactions = client.messagesByType("message_reaction")
        .onEach { reaction ->
            // Handle reaction event
            val messageId = reaction.additionalInfo?.get("messageId")
            val emoji = reaction.additionalInfo?.get("emoji")
        }
        .launchIn(viewModelScope)
}
```

**Available convenience flows:**

- `client.textMessages` - Only `message` type
- `client.systemMessages` - Only `system` type
- `client.imageMessages` - Only `image` type
- `client.messageUpdates` - Only `message_updated` events
- `client.typingIndicators` - Only `user_typing` events
- `client.stoppedTypingIndicators` - Only `user_stopped_typing` events
- `client.messagesByType(type: String)` - Filter by any custom type

### Sending Custom Events

Use `sendEvent()` to send any custom event type to all connected clients:

```kotlin
// Send typing indicator
client.sendTypingIndicator()

// Send stopped typing
client.sendStoppedTypingIndicator()

// Send custom reaction event
client.sendEvent(
    eventType = "message_reaction",
    additionalInfo = mapOf(
        "messageId" to "550e8400-e29b-41d4-a716-446655440000",
        "emoji" to "👍"
    )
)

// Send presence update
client.sendEvent(
    eventType = "user_presence",
    message = "away",
    additionalInfo = mapOf(
        "status" to "brb"
    )
)
```

**Method signatures:**

```kotlin
suspend fun sendEvent(
    eventType: String,
    message: String = "",
    additionalInfo: Map<String, String>? = null
): Boolean

suspend fun sendTypingIndicator(): Boolean
suspend fun sendStoppedTypingIndicator(): Boolean
```

All events return `true` if sent successfully, `false` if no active connection or sending failed.

### Receiving Event Notifications

The server automatically broadcasts certain events:

```kotlin
// Listen for message updates (when messages are edited via PATCH API)
client.messageUpdates
    .onEach { updatedMessage ->
        // The server sends a message_updated event when a message is edited
        // Update your local message list
        _messages.update { messages ->
            messages.map {
                if (it.id == updatedMessage.id) updatedMessage else it
            }
        }
    }
    .launchIn(viewModelScope)
```

**Event format:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "message_updated",
  "message": "Updated message text",
  "timestamp": "2025-01-21T15:30:00Z",
  "user": {
    "id": "user-uuid",
    "name": "Alice"
  },
  "additionalInfo": {
    "custom": "metadata"
  }
}
```

### Use Cases

**Typing Indicators:**

```kotlin
// In your composable
TextField(
    value = message,
    onValueChange = { newValue ->
        message = newValue
        if (newValue.isNotEmpty()) {
            viewModel.sendTyping()
        } else {
            viewModel.sendStoppedTyping()
        }
    }
)

// In your ViewModel
fun sendTyping() = viewModelScope.launch {
    client.sendTypingIndicator()
}

fun sendStoppedTyping() = viewModelScope.launch {
    client.sendStoppedTypingIndicator()
}

// Display typing users
val typingUsers by viewModel.typingUsers.collectAsState()
if (typingUsers.isNotEmpty()) {
    Text("${typingUsers.joinToString(", ")} is typing...")
}
```

**Message Reactions:**

```kotlin
// Send reaction
fun reactToMessage(messageId: String, emoji: String) = viewModelScope.launch {
    client.sendEvent(
        eventType = "message_reaction",
        additionalInfo = mapOf(
            "messageId" to messageId,
            "emoji" to emoji
        )
    )
}

// Receive reactions
client.messagesByType("message_reaction")
    .onEach { reaction ->
        val messageId = reaction.additionalInfo?.get("messageId")
        val emoji = reaction.additionalInfo?.get("emoji")
        // Update UI with reaction
    }
    .launchIn(viewModelScope)
```

**Presence Updates:**

```kotlin
// Send presence
fun updatePresence(status: String) = viewModelScope.launch {
    client.sendEvent(
        eventType = "user_presence",
        message = status,
        additionalInfo = mapOf("lastSeen" to System.currentTimeMillis().toString())
    )
}

// Receive presence
client.messagesByType("user_presence")
    .onEach { presence ->
        val userId = presence.user.id
        val status = presence.message
        // Update user presence in UI
    }
    .launchIn(viewModelScope)
```

## Development

- Build and lint: `./gradlew clean lint`
- Check publishing locally: `./gradlew publishToMavenLocal`

### Required Environment Variables / gradle.properties

```
mavenCentralUsername=<sonatype-username>
mavenCentralPassword=<sonatype-password>

signing.keyId=<last 7 digits from GPG public key>
signing.password=<pgp-passphrase>
signing.secretKeyRingFile=<path to GPG keyring file>
```

Alternatively export them as environment variables (`ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`) for CI.

## Releasing

1. Bump the version in `build.gradle.kts`.
2. Commit and tag the release (`git tag v0.1.2 && git push --tags`).
3. (Optional) Run `./gradlew publishToMavenCentral` locally.
4. Use GitHub release or manual workflow dispatch to trigger `.github/workflows/publish.yml`.
5. In Sonatype Central (<https://s01.oss.sonatype.org/>), close and release the staged repository so it syncs to Maven Central.
