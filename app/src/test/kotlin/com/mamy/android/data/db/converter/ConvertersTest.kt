package com.mamy.android.data.db.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConvertersTest {

    private val c = Converters()

    @Test
    fun `instantToLong round-trips`() {
        val now = Instant.parse("2026-05-02T18:30:45Z")
        val asLong = c.instantToLong(now)
        assertEquals(now, c.longToInstant(asLong))
    }

    @Test
    fun `instantToLong handles null`() {
        assertNull(c.instantToLong(null))
        assertNull(c.longToInstant(null))
    }

    @Test
    fun `uuidToString round-trips`() {
        val u = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertEquals(u, c.stringToUuid(c.uuidToString(u)))
    }

    @Test
    fun `uuidToString handles null`() {
        assertNull(c.uuidToString(null))
        assertNull(c.stringToUuid(null))
    }
}
