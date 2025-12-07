# Chat WS Android

Lightweight Kotlin library that connects Android clients to the [Chat websocket backend](https://github.com/choffmann/chat-room). It provides a simple `ChatWsClient` for websocket communication, along with serializable models that match the server payloads.

## Gradle Setup

```kotlin
// build.gradle.kts of the consuming module
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.choffmann:chat-ws-android:0.1.4"
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
    val currentUser: Flow<User?>

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
    override val currentUser: Flow<User?> = client.currentUser

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

    val currentUser: StateFlow<User?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

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

### Getting Current User Information

After successfully joining a room, you can access the current user's information (including server-assigned names for anonymous joins):

```kotlin
client.currentUser.collectLatest { user ->
    if (user != null) {
        println("Connected as: ${user.name} (ID: ${user.id})")
    } else {
        println("Not connected")
    }
}
```

This is especially useful when joining anonymously, as the server assigns a random name (e.g., "Kotlin Kevin", "Gradle Gero") that you'll want to display in your UI.

**Note:** The library automatically sets the `userInfo=true` query parameter when joining a room, which tells the server to send your user information. The library then filters out your own join notification from the `incomingMessages` flow. When you join a room, the server sends a special join message with a `self` flag that the library uses to extract your user information. This message is not forwarded to `incomingMessages`, so you won't see "You joined room 1" in your message list. Other users will still see the normal join notification.

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

- `type`: Either `"message"` (text), `"image"` (Base64-encoded image data), or `"system"` (system messages)
- `message`: The actual content (text string or Base64 image data)
- `additionalInfo` (optional): Key-value pairs for custom metadata that will be broadcast to all participants

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

Incoming messages with `type = MessageType.IMAGE` contain Base64-encoded image data in the `message` field. Additional metadata like MIME type is available in `additionalInfo`:

```kotlin
client.incomingMessages
    .onEach { message ->
        when (message.type) {
            MessageType.MESSAGE -> {
                // Handle text message
                println("Text: ${message.message}")
            }
            MessageType.IMAGE -> {
                // Handle image message - message field contains Base64 data
                val imageBytes = Base64.decode(message.message, Base64.DEFAULT)
                val mimeType = message.additionalInfo?.get("mimeType") ?: "image/jpeg"
                // Display or save the image
            }
            MessageType.SYSTEM -> {
                // Handle system message
                println("System: ${message.message}")
            }
        }
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
