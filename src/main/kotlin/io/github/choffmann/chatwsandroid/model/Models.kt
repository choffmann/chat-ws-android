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
 * @property id Stable identifier provided by the backend.
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
 * Type of message (system, user message, or image).
 */
@Serializable
enum class MessageType {
    @SerialName("system")
    SYSTEM,

    @SerialName("message")
    MESSAGE,

    @SerialName("image")
    IMAGE
}

/**
 * Domain model describing a chat message received from the server.
 *
 * @property type Message type (system, message, or image).
 * @property message Message content (text for regular messages, Base64-encoded data for images).
 * @property timestamp Timestamp supplied by the backend in UTC.
 * @property user Sender information.
 * @property additionalInfo Optional key-value pairs for additional metadata (e.g., mimeType, imageData for images).
 */
@Serializable
data class Message(
    val type: MessageType,
    val message: String,
    @Serializable(with = UtcLocalDateTimeAsInstantString::class)
    val timestamp: LocalDateTime,
    val user: User,
    val additionalInfo: Map<String, String>? = null
)

/**
 * Model for outgoing messages sent to the server.
 *
 * @property type Message type being sent (system, message, or image).
 * @property message The message content (text for message type, Base64-encoded data for image type).
 * @property additionalInfo Optional key-value pairs for additional metadata that will be broadcast to all participants.
 */
@Serializable
data class OutgoingMessage(
    val type: MessageType,
    val message: String,
    val additionalInfo: Map<String, String>? = null
)
