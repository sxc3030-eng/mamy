package com.mamy.android.data.calendar.google

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CalendarTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,        // epoch millis
    val scope: String,
    val accountEmail: String
) {
    /**
     * Considered expired if [now] is within [skewSeconds] seconds of [expiresAt] (clock-skew safe).
     */
    fun isExpired(now: Instant = Instant.now(), skewSeconds: Long = 60): Boolean {
        return now.toEpochMilli() >= expiresAt - skewSeconds * 1000
    }
}
