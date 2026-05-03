package com.mamy.android.data.calendar.google

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes minimal event with start and end dateTime`() {
        val raw = """{
            "id": "evt-1",
            "summary": "1:1 Marie",
            "start": {"dateTime": "2026-05-02T10:00:00-04:00"},
            "end":   {"dateTime": "2026-05-02T10:30:00-04:00"},
            "attendees": [
              {"email": "marie@example.com", "displayName": "Marie Dubois", "responseStatus": "accepted"},
              {"email": "marc@example.com", "self": true, "responseStatus": "accepted"}
            ]
        }"""
        val ev = json.decodeFromString(CalendarEvent.serializer(), raw)
        assertEquals("evt-1", ev.id)
        assertEquals("1:1 Marie", ev.summary)
        assertEquals("2026-05-02T10:00:00-04:00", ev.start?.dateTime)
        assertEquals(2, ev.attendees?.size)
        assertEquals("marie@example.com", ev.attendees!![0].email)
        assertEquals(true, ev.attendees!![1].self)
    }

    @Test
    fun `decodes events list response with nextPageToken and nextSyncToken`() {
        val raw = """{
            "items": [],
            "nextPageToken": "page-2",
            "nextSyncToken": "sync-xyz"
        }"""
        val list = json.decodeFromString(CalendarEventsList.serializer(), raw)
        assertEquals(0, list.items.size)
        assertEquals("page-2", list.nextPageToken)
        assertEquals("sync-xyz", list.nextSyncToken)
    }

    @Test
    fun `decodes cancelled event with status only`() {
        val raw = """{"id":"evt-9","status":"cancelled"}"""
        val ev = json.decodeFromString(CalendarEvent.serializer(), raw)
        assertEquals("cancelled", ev.status)
    }
}
