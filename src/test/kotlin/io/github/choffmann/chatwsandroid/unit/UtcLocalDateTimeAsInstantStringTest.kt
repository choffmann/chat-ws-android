package io.github.choffmann.chatwsandroid.unit

import io.github.choffmann.chatwsandroid.model.UtcLocalDateTimeAsInstantString
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UtcLocalDateTimeAsInstantStringTest {

    private val json = Json

    @Test
    fun `parses Z suffix`() {
        val s = "\"2026-05-11T10:00:00Z\""
        val ldt = json.decodeFromString(UtcLocalDateTimeAsInstantString, s)
        assertEquals(LocalDateTime(2026, 5, 11, 10, 0, 0), ldt)
    }

    @Test
    fun `parses explicit +00 00 offset to same UTC instant`() {
        val s = "\"2026-05-11T10:00:00+00:00\""
        val ldt = json.decodeFromString(UtcLocalDateTimeAsInstantString, s)
        assertEquals(LocalDateTime(2026, 5, 11, 10, 0, 0), ldt)
    }

    @Test
    fun `parses positive offset and converts to UTC`() {
        val s = "\"2026-05-11T12:00:00+02:00\""
        val ldt = json.decodeFromString(UtcLocalDateTimeAsInstantString, s)
        assertEquals(LocalDateTime(2026, 5, 11, 10, 0, 0), ldt)
    }

    @Test
    fun `parses fractional seconds`() {
        val s = "\"2026-05-11T10:00:00.123Z\""
        val ldt = json.decodeFromString(UtcLocalDateTimeAsInstantString, s)
        assertEquals(2026, ldt.year)
        assertEquals(0, ldt.second)
    }

    @Test
    fun `serializes back as UTC ISO-8601 string`() {
        val ldt = LocalDateTime(2026, 5, 11, 10, 0, 0)
        val encoded = json.encodeToString(UtcLocalDateTimeAsInstantString, ldt)
        // Either "2026-05-11T10:00:00Z" or "2026-05-11T10:00:00.000Z" — both are valid UTC
        assertTrue(encoded.startsWith("\"2026-05-11T10:00:00"))
        assertTrue(encoded.endsWith("Z\""))
    }
}
