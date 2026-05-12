package io.github.choffmann.chatwsandroid.e2e

import io.github.choffmann.chatwsandroid.support.ServerContainer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@Tag("e2e")
class SmokeContainerTest {

    companion object {
        @Container
        @JvmStatic
        val server = ServerContainer()
    }

    @Test
    fun `container starts and exposes a port`() {
        assertTrue(server.isRunning)
        assertTrue(server.firstMappedPort > 0)
    }
}
