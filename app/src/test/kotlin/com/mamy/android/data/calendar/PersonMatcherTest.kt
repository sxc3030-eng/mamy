package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarAttendee
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PersonMatcherTest {

    private val dao = mockk<PersonDao>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
    private val matcher = PersonMatcher(dao, clock)

    @Test
    fun `returns existing person when email already known`() = runTest {
        val existing = PersonEntity(
            id = UUID.randomUUID(),
            name = "Marie Dubois",
            email = "marie@x.com",
            roleHint = null,
            calendarAttendeeId = "marie@x.com",
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            lastInteractionAt = null,
            interactionCount = 0,
            emotionalTrend = null,
            unmatched = false,
            archived = false
        )
        coEvery { dao.findByCalendarEmail("marie@x.com") } returns existing
        val result = matcher.matchOrCreate(
            CalendarAttendee(email = "marie@x.com", displayName = "Marie Dubois")
        )
        assertEquals(existing.id, result?.id)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `creates unmatched stub when no email match (uses displayName)`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        val attendee = CalendarAttendee(email = "marc.tremblay@x.com", displayName = "Marc Tremblay")
        val result = matcher.matchOrCreate(attendee)

        assertNotNull(result)
        assertEquals("Marc Tremblay", captured.captured.name)
        assertEquals("marc.tremblay@x.com", captured.captured.email)
        assertEquals("marc.tremblay@x.com", captured.captured.calendarAttendeeId)
        assertTrue(captured.captured.unmatched)
        assertEquals(Instant.parse("2026-05-02T12:00:00Z"), captured.captured.createdAt)
    }

    @Test
    fun `derives name from email when displayName missing`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        val attendee = CalendarAttendee(email = "marc.tremblay@x.com", displayName = null)
        matcher.matchOrCreate(attendee)
        assertEquals("Marc Tremblay", captured.captured.name)
    }

    @Test
    fun `derives single-word name from email local-part with no separator`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit
        matcher.matchOrCreate(CalendarAttendee(email = "luc@x.com"))
        assertEquals("Luc", captured.captured.name)
    }

    @Test
    fun `derives name from email with underscore separator`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit
        matcher.matchOrCreate(CalendarAttendee(email = "anais_brunet@x.com"))
        assertEquals("Anais Brunet", captured.captured.name)
    }

    @Test
    fun `returns null when attendee has no email and no displayName`() = runTest {
        val result = matcher.matchOrCreate(CalendarAttendee(email = null, displayName = null))
        assertNull(result)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `skips self attendee`() = runTest {
        val result = matcher.matchOrCreate(
            CalendarAttendee(email = "marc@x.com", self = true)
        )
        assertNull(result)
    }

    @Test
    fun `skips resource attendee`() = runTest {
        val result = matcher.matchOrCreate(
            CalendarAttendee(email = "room-1@resource.calendar.google.com", resource = true)
        )
        assertNull(result)
    }
}
