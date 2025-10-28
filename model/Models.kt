package io.github.choffmann.chatwsandroid.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant

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
 * Serialiser that encodes a [LocalDateTime] as an ISO-8601 UTC instant string.
 * This matches the payload emitted by the backend.
 */
object UtcLocalDateTimeAsInstantString : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("UtcLocalDateTime", PrimitiveKind.STRING)

    /**
     * Parses the instant string received from the wire into a [LocalDateTime] in UTC.
     */
    override fun deserialize(decoder: Decoder): LocalDateTime {
        val s = decoder.decodeString()
        return Instant.parse(s).toLocalDateTime(TimeZone.UTC)
    }

    /**
     * Converts the provided [LocalDateTime] to a UTC instant string accepted by the backend.
     */
    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        val iso = value.toInstant(TimeZone.UTC).toString()
        encoder.encodeString(iso)
    }
}

/**
 * Domain model describing a chat message exchanged via websocket.
 *
 * @property type Logical message type (e.g. `message`, `system`).
 * @property message Human readable payload content.
 * @property timestamp Timestamp supplied by the backend in UTC.
 * @property user Sender information.
 */
@Serializable
data class Message(
    val type: String = "message", // "message", "system"
    val message: String,
    @Serializable(with = UtcLocalDateTimeAsInstantString::class)
    val timestamp: LocalDateTime,
    val user: User,
)
