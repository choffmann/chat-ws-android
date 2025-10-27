package de.hsfl.mobilecomputing.chatws.api

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Disconnected(val cause: Throwable?) : ConnectionState
}

