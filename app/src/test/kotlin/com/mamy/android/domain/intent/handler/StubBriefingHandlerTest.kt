package com.mamy.android.domain.intent.handler

import com.mamy.android.domain.intent.Intent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class StubBriefingHandlerTest {

    @Test
    fun `daily brief stub returns not implemented message`() = runTest {
        val result = StubDailyBriefHandler().handle(Intent.DailyBrief("MamY, ma journée"))
        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("not yet", ignoreCase = true) ||
                   result.spokenText!!.contains("pas encore", ignoreCase = true))
    }

    @Test
    fun `next brief stub returns message`() = runTest {
        val result = StubNextBriefHandler().handle(Intent.NextBrief("MamY, briefe"))
        assertTrue(result.success)
        assertTrue(result.spokenText != null)
    }

    @Test
    fun `eod summary stub returns message`() = runTest {
        val result = StubEodSummaryHandler().handle(Intent.EodSummary("MamY, résume ma journée"))
        assertTrue(result.success)
        assertTrue(result.spokenText != null)
    }
}
