package com.mamy.android.domain.briefing

import com.mamy.android.data.calendar.CalendarRepository
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.tts.TtsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

class PreMeetingBriefHandlerTest {

    private val now = Instant.parse("2026-05-02T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val cal = mockk<CalendarRepository>()
    private val gen = mockk<BriefingGenerator>()
    private val tts = mockk<TtsService>(relaxed = true)
    private val sut = PreMeetingBriefHandler(cal, gen, tts, clock)

    @Test
    fun `no upcoming meeting speaks fallback FR`() = runTest {
        coEvery { cal.upcomingMeetings(any()) } returns emptyList()
        val res = sut.run(Locale.FRENCH)
        val text = res.spokenText!!
        assertTrue(text.contains("Aucune"))
        coVerify { tts.speak(text, Locale.FRENCH, interrupt = true) }
    }

    @Test
    fun `picks first future meeting and generates pre meeting brief`() = runTest {
        val mid = UUID.randomUUID()
        val m = MeetingEntity(mid, "evt", "1:1 Marie", now.plusSeconds(300), now.plusSeconds(2100), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(m)
        coEvery { gen.generate(BriefingRequest(BriefingType.PRE_MEETING, mid.toString(), now, Locale.ENGLISH)) } returns
            BriefingResult("Marie 5 min", now, now.plusSeconds(3600), false, "claude", 3)
        val res = sut.run(Locale.ENGLISH)
        assertEquals("Marie 5 min", res.spokenText)
    }

    @Test
    fun `skips already-started meeting`() = runTest {
        val past = MeetingEntity(UUID.randomUUID(), null, "ongoing", now.minusSeconds(60), now.plusSeconds(900), null, null, now)
        coEvery { cal.upcomingMeetings(any()) } returns listOf(past)
        val res = sut.run(Locale.ENGLISH)
        val text = res.spokenText!!
        assertTrue(text.contains("No meeting"))
    }
}
