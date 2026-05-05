package com.mamy.android.domain.intent

import com.mamy.android.domain.intent.handler.ActionsOpenHandler
import com.mamy.android.domain.intent.handler.CaptureHandler
import com.mamy.android.domain.intent.handler.CorrectLastHandler
import com.mamy.android.domain.intent.handler.DailyBriefHandler
import com.mamy.android.domain.intent.handler.EodSummaryHandler
import com.mamy.android.domain.intent.handler.NextBriefHandler
import com.mamy.android.domain.intent.handler.PersonBriefHandler
import com.mamy.android.domain.intent.handler.PromisesOwedMeHandler
import com.mamy.android.domain.intent.handler.TextToHandler
import com.mamy.android.domain.intent.handler.UndoLastHandler
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * 7 spec-canonical voice inputs flow Router → Dispatcher → Handler.
 * Handlers are mocked to return canned replies — we verify only that the right one fires.
 */
class IntentEndToEndTest {

    private val capture: CaptureHandler = mockk()
    private val daily: DailyBriefHandler = mockk()
    private val next: NextBriefHandler = mockk()
    private val personBrief: PersonBriefHandler = mockk()
    private val owed: PromisesOwedMeHandler = mockk()
    private val open: ActionsOpenHandler = mockk()
    private val eod: EodSummaryHandler = mockk()
    private val undo: UndoLastHandler = mockk()
    private val correct: CorrectLastHandler = mockk()
    private val textTo: TextToHandler = mockk()

    private val router = IntentRouter()
    private val dispatcher = IntentDispatcher(
        capture, daily, next, personBrief, owed, open, eod, undo, correct, textTo,
    )

    @Test
    fun `5 FR + 2 EN canonical inputs route end-to-end`() = runTest {
        coEvery { capture.handle(any()) } returns IntentResult.spoken("Noté")
        coEvery { daily.handle(any()) } returns IntentResult.spoken("daily")
        coEvery { next.handle(any()) } returns IntentResult.spoken("next")
        coEvery { personBrief.handle(any()) } returns IntentResult.spoken("brief Marie")
        coEvery { owed.handle(any()) } returns IntentResult.spoken("owed")
        coEvery { open.handle(any()) } returns IntentResult.spoken("open")
        coEvery { eod.handle(any()) } returns IntentResult.spoken("eod")
        coEvery { undo.handle(any()) } returns IntentResult.spoken("undo")
        coEvery { correct.handle(any()) } returns IntentResult.spoken("correct")

        val transcripts = listOf(
            "MamY, prends note projet X avance bien" to "Noté",
            "MamY, ma journée" to "daily",
            "MamY, briefe" to "next",
            "MamY, briefe-moi sur Marie" to "brief Marie",
            "MamY, qui me devait quoi" to "owed",
            "MamY, my open actions" to "open",
            "MamY, summarize my day" to "eod",
        )
        for ((tx, expected) in transcripts) {
            val intent = router.classify(tx)
            val result = dispatcher.dispatch(intent)
            assertTrue(
                result.spokenText == expected,
                "transcript [$tx] → intent ${intent::class.simpleName} → got [${result.spokenText}], expected [$expected]",
            )
        }
    }
}
