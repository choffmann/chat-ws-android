package io.github.choffmann.chatwsandroid.support

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

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

    companion object {
        private const val EXPOSED_PORT = 8080
    }
}
