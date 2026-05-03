package com.mamy.android.data.llm.cost

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlmCostTrackerTest {

    @Test
    fun `record inserts entry with computed cost`() = runTest {
        val dao = mockk<LlmCostDao>(relaxed = true)
        val fixedNow = Instant.parse("2026-05-15T12:00:00Z")
        val tracker = LlmCostTracker(
            dao = dao,
            calculator = LlmCostCalculator(),
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        )

        tracker.record(provider = "claude", tokensIn = 1000, tokensOut = 500)

        val captured = slot<LlmCostEntry>()
        coVerify { dao.insert(capture(captured)) }
        assertEquals("claude", captured.captured.provider)
        assertEquals(1000, captured.captured.tokensIn)
        assertEquals(500, captured.captured.tokensOut)
        assertEquals(3500L, captured.captured.costMicroCents)
        assertEquals(fixedNow, captured.captured.createdAt)
    }
}
