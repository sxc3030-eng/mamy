package com.mamy.android.service.work

import com.mamy.android.data.calendar.CalendarRepository
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.data.settings.SettingsSnapshot
import com.mamy.android.domain.briefing.BriefingGenerator
import com.mamy.android.domain.briefing.BriefingRequest
import com.mamy.android.domain.briefing.BriefingResult
import com.mamy.android.domain.briefing.BriefingType
import com.mamy.android.service.notif.BriefingNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class PreMeetingSchedulerTest {

    private val now = Instant.parse("2026-05-02T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val cal = mockk<CalendarRepository>()
    private val gen = mockk<BriefingGenerator>()
    private val notifier = mockk<BriefingNotifier>(relaxed = true)
    private val settings = mockk<SettingsRepository>()

    private val sut = PreMeetingScheduler(cal, gen, notifier, settings, clock)

    @Test
    fun `meeting starting in 4_5 min triggers briefing and notif`() = runTest {
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, Locale.FRENCH, 1f, ZoneId.of("UTC"))
        val mid = UUID.randomUUID()
        val m = MeetingEntity(mid, null, "1:1 Marie",
            startsAt = now.plusSeconds(270), // 4 min 30 sec
            endsAt = now.plusSeconds(2070), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(m)
        coEvery { gen.generate(BriefingRequest(BriefingType.PRE_MEETING, mid.toString(), now, Locale.FRENCH)) } returns
            BriefingResult("Marie", now, now.plusSeconds(3600), false, "claude", 3)

        sut.check()

        coVerify { gen.generate(any()) }
        coVerify { notifier.postPreMeetingReady(m, Locale.FRENCH) }
    }

    @Test
    fun `meeting at exactly 4 min boundary fires`() = runTest {
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, Locale.ENGLISH, 1f, ZoneId.of("UTC"))
        val mid = UUID.randomUUID()
        val m = MeetingEntity(mid, null, "x", now.plusSeconds(240), now.plusSeconds(2040), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(m)
        coEvery { gen.generate(any()) } returns BriefingResult("x", now, now, false, "gpt", 1)
        sut.check()
        coVerify { gen.generate(any()) }
    }

    @Test
    fun `meeting at 5 min boundary does NOT fire (exclusive)`() = runTest {
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, Locale.ENGLISH, 1f, ZoneId.of("UTC"))
        val m = MeetingEntity(UUID.randomUUID(), null, "x", now.plusSeconds(300), now.plusSeconds(2100), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(m)
        sut.check()
        coVerify(exactly = 0) { gen.generate(any()) }
    }

    @Test
    fun `meeting in 6 min does NOT fire`() = runTest {
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, Locale.ENGLISH, 1f, ZoneId.of("UTC"))
        val m = MeetingEntity(UUID.randomUUID(), null, "x", now.plusSeconds(360), now.plusSeconds(2160), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(m)
        sut.check()
        coVerify(exactly = 0) { gen.generate(any()) }
    }

    @Test
    fun `generator failure swallowed, no notif`() = runTest {
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, Locale.ENGLISH, 1f, ZoneId.of("UTC"))
        val m = MeetingEntity(UUID.randomUUID(), null, "x", now.plusSeconds(270), now.plusSeconds(2070), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(m)
        coEvery { gen.generate(any()) } throws IllegalStateException("api")
        sut.check() // must not throw
        coVerify(exactly = 0) { notifier.postPreMeetingReady(any(), any()) }
    }
}
