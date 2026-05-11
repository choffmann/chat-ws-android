package io.github.choffmann.chatwsandroid.unit

import io.github.choffmann.chatwsandroid.ConnectionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ConnectionStateTest {

    @Test
    fun `Idle is a singleton`() {
        assertSame(ConnectionState.Idle, ConnectionState.Idle)
    }

    @Test
    fun `Connecting is a singleton`() {
        assertSame(ConnectionState.Connecting, ConnectionState.Connecting)
    }

    @Test
    fun `Connected is a singleton`() {
        assertSame(ConnectionState.Connected, ConnectionState.Connected)
    }

    @Test
    fun `Disconnected with same cause is equal`() {
        val cause = RuntimeException("boom")
        assertEquals(ConnectionState.Disconnected(cause), ConnectionState.Disconnected(cause))
    }

    @Test
    fun `Disconnected with different causes is not equal`() {
        assertNotEquals(
            ConnectionState.Disconnected(RuntimeException("a")),
            ConnectionState.Disconnected(RuntimeException("b"))
        )
    }

    @Test
    fun `Disconnected accepts null cause for graceful close`() {
        val d = ConnectionState.Disconnected(null)
        assertNull(d.cause)
    }
}
