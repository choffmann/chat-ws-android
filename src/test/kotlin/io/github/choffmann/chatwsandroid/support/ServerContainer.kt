package io.github.choffmann.chatwsandroid.support

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.HttpURLConnection
import java.net.URL

/**
 * Testcontainer wrapper for the real chat-room server. Instantiate once per
 * test class via `@Container` (JUnit 5 Testcontainers extension).
 */
class ServerContainer : GenericContainer<ServerContainer>(
    DockerImageName.parse("ghcr.io/choffmann/chat-room:latest")
) {
    init {
        withExposedPorts(EXPOSED_PORT)
        waitingFor(Wait.forLogMessage(".*server listening.*", 1))
    }

    /** The `ws://host:mappedPort` base URL for the running server. */
    fun baseWsUrl(): String = "ws://${host}:${getMappedPort(EXPOSED_PORT)}"

    /** The `http://host:mappedPort` base URL for REST calls. */
    fun baseHttpUrl(): String = "http://${host}:${getMappedPort(EXPOSED_PORT)}"

    /**
     * Creates a room on the running server and returns the server-assigned room ID.
     * The server auto-increments IDs; [name] is stored as metadata only.
     * Throws if the server rejects the request.
     */
    fun createRoom(name: String = "test"): Int {
        val body = """{"id":0,"name":"$name"}""".toByteArray(Charsets.UTF_8)
        val conn = URL("${baseHttpUrl()}/api/v1/rooms").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val responseBody = conn.inputStream.bufferedReader().readText()
            check(code in 200..299) {
                "createRoom($name) failed: HTTP $code — $responseBody"
            }
            // Response: {"roomID":N}
            val match = Regex(""""roomID"\s*:\s*(\d+)""").find(responseBody)
            return checkNotNull(match?.groupValues?.get(1)?.toInt()) {
                "createRoom($name): could not parse roomID from response: $responseBody"
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val EXPOSED_PORT = 8080
    }
}
