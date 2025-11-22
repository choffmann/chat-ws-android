package io.github.choffmann.chatwsandroid.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Representation of a chat participant.
 *
 * @property id Stable UUID identifier provided by the backend.
 * @property name Display name that should be shown in clients.
 */
@Serializable
data class User(
    val id: String,
    val name: String,
)

/**
 * Serializer that encodes a [LocalDateTime] as an ISO-8601 UTC instant string.
 * This matches the payload emitted by the backend.
 */
object UtcLocalDateTimeAsInstantString : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("UtcLocalDateTime", PrimitiveKind.STRING)

    /**
     * Parses the instant string received from the wire into a [LocalDateTime] in UTC.
     */
    @OptIn(ExperimentalTime::class)
    override fun deserialize(decoder: Decoder): LocalDateTime {
        val s = decoder.decodeString()
        return Instant.parse(s).toLocalDateTime(TimeZone.UTC)
    }

    /**
     * Converts the provided [LocalDateTime] to a UTC instant string accepted by the backend.
     */
    @OptIn(ExperimentalTime::class)
    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        val iso = value.toInstant(TimeZone.UTC).toString()
        encoder.encodeString(iso)
    }
}

/**
 * Convenience constants for common message types.
 *
 * These are the standard message types supported by the backend:
 * - [SYSTEM]: System notifications (e.g., user joined/left)
 * - [MESSAGE]: Regular text messages from users
 * - [IMAGE]: Image messages (Base64-encoded)
 *
 * The following event types are ephemeral (not stored in message history):
 * - [MESSAGE_UPDATED]: Notification that a message was edited
 * - [MESSAGE_DELETED]: Notification that a message was deleted
 * - [USER_TYPING]: User is currently typing
 * - [USER_STOPPED_TYPING]: User stopped typing
 *
 * You can use any custom string as a message type for application-specific events.
 *
 * @since 0.2.0
 */
object MessageTypes {
    const val SYSTEM = "system"
    const val MESSAGE = "message"
    const val IMAGE = "image"
    const val MESSAGE_UPDATED = "message_updated"
    const val MESSAGE_DELETED = "message_deleted"
    const val USER_TYPING = "user_typing"
    const val USER_STOPPED_TYPING = "user_stopped_typing"
}

/**
 * Deprecated enum for backward compatibility.
 *
 * Please migrate to using [MessageTypes] constants or raw String values instead.
 * See MIGRATION_GUIDE.md for details.
 *
 * @deprecated Since 0.2.0. Use [MessageTypes] constants or String values directly.
 */
@Deprecated(
    message = "Use MessageTypes constants or String values instead",
    replaceWith = ReplaceWith("MessageTypes.SYSTEM", "io.github.choffmann.chatwsandroid.model.MessageTypes"),
    level = DeprecationLevel.WARNING
)
enum class MessageType(val value: String) {
    SYSTEM("system"),
    MESSAGE("message"),
    IMAGE("image")
}

/**
 * Domain model describing a chat message received from the server.
 *
 * @property id Unique UUID identifier for the message assigned by the server (optional for backward compatibility).
 * @property type Message type as a string. Can be any value - use [MessageTypes] constants for standard types.
 * @property message Message content (text for regular messages, Base64-encoded data for images).
 * @property timestamp Timestamp supplied by the backend in UTC.
 * @property user Sender information.
 * @property additionalInfo Optional key-value pairs for additional metadata (e.g., mimeType, imageData for images).
 *
 * @since 0.2.0 Changed type from enum to String for flexibility
 */
@Serializable
data class Message(
    val id: String? = null,
    val type: String,
    val message: String,
    @Serializable(with = UtcLocalDateTimeAsInstantString::class)
    val timestamp: LocalDateTime,
    val user: User,
    val additionalInfo: Map<String, String>? = null
)

/**
 * Model for outgoing messages sent to the server.
 *
 * @property type Message type as a string. Can be any value - use [MessageTypes] constants for standard types.
 * @property message The message content (text for message type, Base64-encoded data for image type).
 * @property additionalInfo Optional key-value pairs for additional metadata that will be broadcast to all participants.
 *
 * @since 0.2.0 Changed type from enum to String for flexibility
 */
@Serializable
data class OutgoingMessage(
    val type: String,
    val message: String,
    val additionalInfo: Map<String, String>? = null
) {
    companion object {
        /**
         * Deprecated factory function for backward compatibility with MessageType enum.
         *
         * @deprecated Since 0.2.0. Use the primary constructor with String type instead.
         */
        @Deprecated(
            message = "Use OutgoingMessage(type: String, ...) with MessageTypes constants or String values instead",
            replaceWith = ReplaceWith("OutgoingMessage(type.value, message, additionalInfo)"),
            level = DeprecationLevel.WARNING
        )
        fun create(
            type: MessageType,
            message: String,
            additionalInfo: Map<String, String>? = null
        ): OutgoingMessage = OutgoingMessage(
            type = type.value,
            message = message,
            additionalInfo = additionalInfo
        )
    }
}
