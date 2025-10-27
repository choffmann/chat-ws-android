package de.hsfl.mobilecomputing.chatws.api

import kotlinx.coroutines.flow.Flow
import com.example.chatws.model.Message

interface MessageRepository {
    suspend fun createRoom(): ApiResponse<CreateRoomResponse>
    fun joinRoom(roomID: Int, userName: String? = null)
    suspend fun sendMessage(message: String): Boolean
    suspend fun disconnect()

    val incomingMessages: Flow<Message>
    val connectionState: Flow<ConnectionState>
}

