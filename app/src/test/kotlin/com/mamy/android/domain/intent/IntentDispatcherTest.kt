package com.mamy.android.domain.intent

import com.mamy.android.domain.intent.handler.ActionsOpenHandler
import com.mamy.android.domain.intent.handler.CaptureHandler
import com.mamy.android.domain.intent.handler.CorrectLastHandler
import com.mamy.android.domain.intent.handler.DailyBriefHandler
import com.mamy.android.domain.intent.handler.EodSummaryHandler
import com.mamy.android.domain.intent.handler.NextBriefHandler
import com.mamy.android.domain.intent.handler.PersonBriefHandler
import com.mamy.android.domain.intent.handler.PromisesOwedMeHandler
import com.mamy.android.domain.intent.handler.UndoLastHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class IntentDispatcherTest {

    private val capture: CaptureHandler = mockk(relaxed = true)
    private val daily: DailyBriefHandler = mockk(relaxed = true)
    private val next: NextBriefHandler = mockk(relaxed = true)
    private val personBrief: PersonBriefHandler = mockk(relaxed = true)
    private val owed: PromisesOwedMeHandler = mockk(relaxed = true)
    private val open: ActionsOpenHandler = mockk(relaxed = true)
    private val eod: EodSummaryHandler = mockk(relaxed = true)
    private val undo: UndoLastHandler = mockk(relaxed = true)
    private val correct: CorrectLastHandler = mockk(relaxed = true)

    private val dispatcher = IntentDispatcher(
        capture, daily, next, personBrief, owed, open, eod, undo, correct,
    )

    @Test
    fun `Capture routes to CaptureHandler`() = runTest {
        coEvery { capture.handle(any()) } returns IntentResult.silent()
        dispatcher.dispatch(Intent.Capture("MamY, prends note x"))
        coVerify { capture.handle(any()) }
    }

    @Test
    fun `DailyBrief routes to DailyBriefHandler`() = runTest {
        coEvery { daily.handle(any()) } returns IntentResult.silent()
        dispatcher.dispatch(Intent.DailyBrief("MamY, ma journée"))
        coVerify { daily.handle(any()) }
    }

    @Test
    fun `PersonBrief routes`() = runTest {
        coEvery { personBrief.handle(any()) } returns IntentResult.silent()
        dispatcher.dispatch(Intent.PersonBrief("Marie", "MamY, briefe-moi sur Marie"))
        coVerify { personBrief.handle(any()) }
    }

    @Test
    fun `PromisesOwedMe routes`() = runTest {
        coEvery { owed.handle(any()) } returns IntentResult.silent()
        dispatcher.dispatch(Intent.PromisesOwedMe("MamY, qui me devait quoi"))
        coVerify { owed.handle(any()) }
    }

    @Test
    fun `UndoLast routes`() = runTest {
        coEvery { undo.handle(any()) } returns IntentResult.silent()
        dispatcher.dispatch(Intent.UndoLast("MamY, oublie ça"))
        coVerify { undo.handle(any()) }
    }

    @Test
    fun `CorrectLast routes`() = runTest {
        coEvery { correct.handle(any()) } returns IntentResult.silent()
        dispatcher.dispatch(Intent.CorrectLast("x", "MamY, modifie : x"))
        coVerify { correct.handle(any()) }
    }
}
