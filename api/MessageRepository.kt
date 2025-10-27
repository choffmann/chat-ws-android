package de.hsfl.mobilecomputing.chatws.api

import kotlinx.coroutines.flow.Flow
import de.hsfl.mobilecomputing.chatws.model.Message

/**
 * Abstraction for exchanging chat messages with the websocket backend.
 * Implementations manage the underlying connection lifecycle and expose state updates as flows.
 */
interface MessageRepository {
    /**
     * Creates a new chat room on the backend.
     *
     * @return [ApiResponse] describing whether the room was created successfully and, if so, its identifier.
     */
    suspend fun createRoom(): ApiResponse<CreateRoomResponse>

    /**
     * Connects the repository to the given room and starts listening for incoming messages.
     *
     * @param roomID Identifier of the room to join.
     * @param userName Optional username that should be announced to other participants.
     */
    fun joinRoom(roomID: Int, userName: String? = null)

    /**
     * Sends a raw message payload through the active websocket session.
     *
     * @param message UTF-8 text payload that will be delivered to the room.
     * @return `true` if the message was enqueued successfully, `false` if the repository is not connected.
     */
    suspend fun sendMessage(message: String): Boolean

    /**
     * Closes the websocket connection and releases associated resources.
     */
    suspend fun disconnect()

    /**
     * Stream of domain [Message] instances received from the websocket connection.
     */
    val incomingMessages: Flow<Message>

    /**
     * Stream that reports the current [ConnectionState] of the repository.
     */
    val connectionState: Flow<ConnectionState>
}
