package de.hsfl.mobilecomputing.chatws.model

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


@Serializable
data class User(
    val id: String,
    val name: String,
)

object UtcLocalDateTimeAsInstantString : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("UtcLocalDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDateTime {
        val s = decoder.decodeString()
        return Instant.parse(s).toLocalDateTime(TimeZone.UTC)
    }
    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        val iso = value.toInstant(TimeZone.UTC).toString()
        encoder.encodeString(iso)
    }
}

@Serializable
data class Message(
    val type: String = "message", // "message", "system"
    val message: String,
    @Serializable(with = UtcLocalDateTimeAsInstantString::class)
    val timestamp: LocalDateTime,
    val user: User,
)

