package io.github.choffmann.chatwsandroid.unit

import io.github.choffmann.chatwsandroid.net.AppJson
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class JsonConfigTest {

    @Serializable
    data class Sample(val a: String, val b: String? = null, val c: Int = 0)

    @Test
    fun `ignores unknown keys`() {
        val s = AppJson.decodeFromString<Sample>("""{"a":"x","unknown":42}""")
        assertEquals("x", s.a)
    }

    @Test
    fun `omits nulls in encoded output`() {
        val encoded = AppJson.encodeToString(Sample.serializer(), Sample(a = "x", b = null))
        assertFalse(encoded.contains("\"b\""), "encoded was: $encoded")
    }

    @Test
    fun `omits default values in encoded output`() {
        val encoded = AppJson.encodeToString(Sample.serializer(), Sample(a = "x", c = 0))
        assertFalse(encoded.contains("\"c\""), "encoded was: $encoded")
    }

    @Test
    fun `is lenient about quoted-vs-unquoted primitives`() {
        // isLenient=true → unquoted strings are accepted
        val s = AppJson.decodeFromString<Sample>("""{a:"x"}""")
        assertEquals("x", s.a)
    }
}
