package com.mamy.android.data.calendar.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: String,
    val status: String? = null,                 // "confirmed" | "cancelled" | "tentative"
    val summary: String? = null,
    val description: String? = null,
    val start: CalendarTime? = null,
    val end: CalendarTime? = null,
    val attendees: List<CalendarAttendee>? = null,
    val organizer: CalendarOrganizer? = null,
    val recurringEventId: String? = null,
    val updated: String? = null
)

@Serializable
data class CalendarTime(
    val dateTime: String? = null,               // ISO-8601 with offset (timed events)
    val date: String? = null,                   // YYYY-MM-DD (all-day events)
    val timeZone: String? = null
)

@Serializable
data class CalendarAttendee(
    val email: String? = null,
    val displayName: String? = null,
    val self: Boolean? = null,
    val organizer: Boolean? = null,
    val resource: Boolean? = null,
    val responseStatus: String? = null,         // "accepted" | "declined" | "needsAction" | "tentative"
    val optional: Boolean? = null
)

@Serializable
data class CalendarOrganizer(
    val email: String? = null,
    val displayName: String? = null,
    val self: Boolean? = null
)

@Serializable
data class CalendarEventsList(
    val items: List<CalendarEvent> = emptyList(),
    val nextPageToken: String? = null,
    val nextSyncToken: String? = null,
    @SerialName("timeZone") val calendarTimeZone: String? = null
)
