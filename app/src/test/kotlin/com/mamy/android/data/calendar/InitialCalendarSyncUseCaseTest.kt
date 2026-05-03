package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarAttendee
import com.mamy.android.data.calendar.google.CalendarEvent
import com.mamy.android.data.calendar.google.CalendarEventsList
import com.mamy.android.data.calendar.google.CalendarTime
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.settings.CalendarSyncStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InitialCalendarSyncUseCaseTest {

    private val api = mockk<CalendarApiClient>()
    private val meetingDao = mockk<MeetingDao>(relaxed = true)
    private val attendeeDao = mockk<MeetingAttendeeDao>(relaxed = true)
    private val matcher = mockk<PersonMatcher>()
    private val state = mockk<CalendarSyncStateStore>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
    private val useCase = InitialCalendarSyncUseCase(api, meetingDao, attendeeDao, matcher, state, clock)

    @Test
    fun `syncs single page, persists meeting + attendees, saves syncToken`() = runTest {
        val ev = CalendarEvent(
            id = "evt-1",
            status = "confirmed",
            summary = "1:1 Marie",
            start = CalendarTime(dateTime = "2026-05-02T10:00:00Z"),
            end = CalendarTime(dateTime = "2026-05-02T10:30:00Z"),
            attendees = listOf(
                CalendarAttendee(email = "marie@x.com", displayName = "Marie"),
                CalendarAttendee(email = "marc@x.com", self = true)
            )
        )
        coEvery { api.listEvents("primary", any(), any(), null, null, any()) } returns
            Result.success(CalendarEventsList(items = listOf(ev), nextSyncToken = "sync-1"))
        coEvery { matcher.matchOrCreate(match { it.email == "marie@x.com" }) } returns PersonEntity(
            id = UUID.randomUUID(), name = "Marie", email = "marie@x.com", roleHint = null,
            calendarAttendeeId = "marie@x.com", createdAt = clock.instant(),
            lastInteractionAt = null, interactionCount = 0, emotionalTrend = null,
            unmatched = false, archived = false
        )
        coEvery { matcher.matchOrCreate(match { it.self == true }) } returns null
        coEvery { meetingDao.findByCalendarEventId("evt-1") } returns null

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        coVerify { meetingDao.upsert(match<MeetingEntity> { it.calendarEventId == "evt-1" }) }
        coVerify { attendeeDao.insertAll(match { it.size == 1 }) }
        coVerify { state.saveSyncToken("primary", "sync-1") }
    }

    @Test
    fun `paginates when nextPageToken returned`() = runTest {
        coEvery { api.listEvents("primary", any(), any(), null, null, any()) } returns
            Result.success(CalendarEventsList(items = emptyList(), nextPageToken = "p2"))
        coEvery { api.listEvents("primary", any(), any(), null, "p2", any()) } returns
            Result.success(CalendarEventsList(items = emptyList(), nextSyncToken = "sync-final"))

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        coVerify { state.saveSyncToken("primary", "sync-final") }
    }

    @Test
    fun `cancelled events delete prior meeting`() = runTest {
        val cancelled = CalendarEvent(id = "evt-9", status = "cancelled")
        coEvery { api.listEvents(any(), any(), any(), null, null, any()) } returns
            Result.success(CalendarEventsList(items = listOf(cancelled), nextSyncToken = "s"))
        useCase.execute()
        coVerify { meetingDao.deleteByCalendarEventId("evt-9") }
    }

    @Test
    fun `propagates failure from API`() = runTest {
        coEvery { api.listEvents(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))
        val res = useCase.execute()
        assertTrue(res.isFailure)
    }
}
