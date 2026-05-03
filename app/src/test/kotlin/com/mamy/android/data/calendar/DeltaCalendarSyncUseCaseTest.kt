package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarEventsList
import com.mamy.android.data.calendar.google.CalendarSyncTokenInvalidException
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.settings.CalendarSyncStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeltaCalendarSyncUseCaseTest {

    private val api = mockk<CalendarApiClient>(relaxed = true)
    private val state = mockk<CalendarSyncStateStore>(relaxed = true)
    private val initial = mockk<InitialCalendarSyncUseCase>(relaxed = true)
    private val meetingDao = mockk<MeetingDao>(relaxed = true)
    private val attendeeDao = mockk<MeetingAttendeeDao>(relaxed = true)
    private val matcher = mockk<PersonMatcher>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
    private val useCase = DeltaCalendarSyncUseCase(
        api, state, initial, meetingDao, attendeeDao, matcher, clock
    )

    @Test
    fun `delegates to initial sync when no stored sync token`() = runTest {
        every { state.loadSyncToken("primary") } returns null
        coEvery { initial.execute("primary", any(), any()) } returns Result.success(Unit)
        val res = useCase.execute()
        assertTrue(res.isSuccess)
        coVerify { initial.execute("primary", any(), any()) }
        coVerify(exactly = 0) { api.listEvents(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `runs delta with stored sync token, updates token`() = runTest {
        every { state.loadSyncToken("primary") } returns "abc"
        coEvery {
            api.listEvents("primary", null, null, "abc", null, any())
        } returns Result.success(CalendarEventsList(items = emptyList(), nextSyncToken = "def"))
        val res = useCase.execute()
        assertTrue(res.isSuccess)
        coVerify { state.saveSyncToken("primary", "def") }
    }

    @Test
    fun `falls back to initial sync when token returns 410`() = runTest {
        every { state.loadSyncToken("primary") } returns "stale"
        coEvery {
            api.listEvents("primary", null, null, "stale", null, any())
        } returns Result.failure(CalendarSyncTokenInvalidException())
        coEvery { initial.execute("primary", any(), any()) } returns Result.success(Unit)
        val res = useCase.execute()
        assertTrue(res.isSuccess)
        coVerify { state.clearSyncToken("primary") }
        coVerify { initial.execute("primary", any(), any()) }
    }
}
