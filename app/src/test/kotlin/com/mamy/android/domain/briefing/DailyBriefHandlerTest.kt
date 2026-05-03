package com.mamy.android.domain.briefing

import com.mamy.android.data.tts.TtsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class DailyBriefHandlerTest {

    private val now = Instant.parse("2026-05-02T13:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val gen = mockk<BriefingGenerator>()
    private val tts = mockk<TtsService>(relaxed = true)
    private val sut = DailyBriefHandler(gen, tts, clock)

    @Test
    fun `run generates briefing then speaks it FR`() = runTest {
        val req = BriefingRequest(BriefingType.DAILY, null, now, Locale.FRENCH)
        coEvery { gen.generate(req) } returns
            BriefingResult("Bonjour Marc", now, now.plusSeconds(8 * 3600), false, "claude", 5)
        val res = sut.run(Locale.FRENCH)
        coVerify { tts.speak("Bonjour Marc", Locale.FRENCH, interrupt = true) }
        // IntentResult is a data class (not a sealed Ok subtype) — read spokenText directly.
        assertEquals("Bonjour Marc", res.spokenText)
    }
}
