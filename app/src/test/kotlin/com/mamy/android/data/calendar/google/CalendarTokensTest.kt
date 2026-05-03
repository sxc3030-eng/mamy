package com.mamy.android.data.calendar.google

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarTokensTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes round-trip`() {
        val original = CalendarTokens(
            accessToken = "ya29.abc",
            refreshToken = "1//xyz",
            expiresAt = Instant.parse("2026-05-02T14:30:00Z").toEpochMilli(),
            scope = "https://www.googleapis.com/auth/calendar.readonly",
            accountEmail = "marc@example.com"
        )
        val encoded = json.encodeToString(CalendarTokens.serializer(), original)
        val decoded = json.decodeFromString(CalendarTokens.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `isExpired true when expiresAt past`() {
        val tokens = CalendarTokens(
            accessToken = "x", refreshToken = "y",
            expiresAt = Instant.now().minusSeconds(60).toEpochMilli(),
            scope = "s", accountEmail = "e@e.com"
        )
        assertEquals(true, tokens.isExpired(now = Instant.now()))
    }

    @Test
    fun `isExpired false when expiresAt future plus skew`() {
        val tokens = CalendarTokens(
            accessToken = "x", refreshToken = "y",
            expiresAt = Instant.now().plusSeconds(120).toEpochMilli(),
            scope = "s", accountEmail = "e@e.com"
        )
        assertEquals(false, tokens.isExpired(now = Instant.now()))
    }
}
