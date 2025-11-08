# Chat WS Android

Lightweight Kotlin library that connects Android clients to the [Chat websocket backend](https://github.com/choffmann/chat-room). It provides a simple `ChatWsClient` for websocket communication, along with serializable models that match the server payloads.

## Gradle Setup

```kotlin
// build.gradle.kts of the consuming module
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.choffmann:chat-ws-android:0.1.1")
}
```

Snapshots (if published) are available from Sonatype:

```kotlin
repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}
```

## Quick Start

Create your own repository interface and implementation:

```kotlin
// Your repository interface
interface ChatRepository {
    val messages: Flow<Message>
    val connectionState: Flow<ConnectionState>

    fun connect(roomId: Int, userName: String)
    suspend fun sendMessage(text: String): Boolean
    suspend fun disconnect()
}

// Your implementation using ChatWsClient
class ChatRepositoryImpl(
    private val client: ChatWsClient = ChatWsClient()
) : ChatRepository {
    override val messages: Flow<Message> = client.incomingMessages
    override val connectionState: Flow<ConnectionState> = client.connectionState

    override fun connect(roomId: Int, userName: String) {
        client.joinRoom(roomId, userName)
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

    fun connect(roomId: Int, userName: String) = repository.connect(roomId, userName)

    fun send(text: String) = viewModelScope.launch { repository.sendMessage(text) }

    override fun onCleared() {
        viewModelScope.launch { repository.disconnect() }
        super.onCleared()
    }
}
```

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

### Sending Images

The library supports sending images through the websocket connection. Images are Base64-encoded and sent as JSON messages.

```kotlin
// Add image sending to your repository
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

// Use it in your ViewModel
fun sendImage(uri: Uri, context: Context) = viewModelScope.launch {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val imageBytes = inputStream?.readBytes()
        inputStream?.close()

        if (imageBytes != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val success = repository.sendImage(imageBytes, mimeType)
            if (success) {
                // Image sent successfully
            } else {
                // Failed to send image (not connected)
            }
        }
    } catch (e: Exception) {
        // Handle error
    }
}
```

### Receiving Images

Incoming messages with `contentType = "image"` contain Base64-encoded image data:

```kotlin
client.incomingMessages
    .onEach { message ->
        when (message.contentType) {
            "text" -> {
                // Handle text message
                println("Text: ${message.message}")
            }
            "image" -> {
                // Handle image message
                message.imageData?.let { base64Data ->
                    val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    // Display or save the image
                }
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
2. Commit and tag the release (`git tag v0.1.1 && git push --tags`).
3. (Optional) Run `./gradlew publishToMavenCentral` locally.
4. Use GitHub release or manual workflow dispatch to trigger `.github/workflows/publish.yml`.
5. In Sonatype Central (<https://s01.oss.sonatype.org/>), close and release the staged repository so it syncs to Maven Central.
