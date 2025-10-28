package io.github.choffmann.chatwsandroid.api

/**
 * Represents the lifecycle of the underlying websocket connection.
 */
sealed interface ConnectionState {
    /**
     * No active connection attempt is in progress.
     */
    data object Idle : ConnectionState

    /**
     * The repository is actively attempting to connect.
     */
    data object Connecting : ConnectionState

    /**
     * The websocket connection is established and ready to exchange messages.
     */
    data object Connected : ConnectionState

    /**
     * The connection terminated, optionally exposing the cause.
     *
     * @param cause Reason for the disconnection, or `null` if the connection closed gracefully.
     */
    data class Disconnected(val cause: Throwable?) : ConnectionState
}
