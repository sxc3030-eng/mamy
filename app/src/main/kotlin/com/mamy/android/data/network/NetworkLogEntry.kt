package com.mamy.android.data.network

import java.time.Instant

/**
 * Lightweight in-memory record of a single outbound HTTP call.
 * P5: only the CALENDAR category is wired (via [com.mamy.android.data.calendar.google.CalendarHttpLogger]);
 * future categories (LLM, STT, etc.) plug in via the same store.
 */
data class NetworkLogEntry(
    val category: Category,
    val timestamp: Instant,
    val method: String,
    val url: String,
    val statusCode: Int,
    val durationMs: Long
) {
    enum class Category { CALENDAR, LLM, STT, OTHER }
}
