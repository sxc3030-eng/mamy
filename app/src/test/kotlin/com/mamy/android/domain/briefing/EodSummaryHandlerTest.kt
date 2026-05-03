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

class EodSummaryHandlerTest {

    private val now = Instant.parse("2026-05-02T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val gen = mockk<BriefingGenerator>()
    private val tts = mockk<TtsService>(relaxed = true)
    private val sut = EodSummaryHandler(gen, tts, clock)

    @Test
    fun `run generates eod and speaks EN`() = runTest {
        coEvery { gen.generate(BriefingRequest(BriefingType.EOD_SUMMARY, null, now, Locale.ENGLISH)) } returns
            BriefingResult("5 ones, 2 actions open", now, now, false, "gpt", 6)
        val res = sut.run(Locale.ENGLISH)
        assertEquals("5 ones, 2 actions open", res.spokenText)
        coVerify { tts.speak("5 ones, 2 actions open", Locale.ENGLISH, interrupt = true) }
    }
}
