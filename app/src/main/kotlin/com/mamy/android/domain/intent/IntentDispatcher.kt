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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single dispatch surface : `dispatch(Intent) -> IntentResult`.
 * The when-on-sealed-class is exhaustive, compiler enforces full coverage.
 */
@Singleton
class IntentDispatcher @Inject constructor(
    private val captureHandler: CaptureHandler,
    private val dailyBriefHandler: DailyBriefHandler,
    private val nextBriefHandler: NextBriefHandler,
    private val personBriefHandler: PersonBriefHandler,
    private val promisesOwedMeHandler: PromisesOwedMeHandler,
    private val actionsOpenHandler: ActionsOpenHandler,
    private val eodSummaryHandler: EodSummaryHandler,
    private val undoLastHandler: UndoLastHandler,
    private val correctLastHandler: CorrectLastHandler,
) {
    suspend fun dispatch(intent: Intent): IntentResult = when (intent) {
        is Intent.Capture -> captureHandler.handle(intent)
        is Intent.DailyBrief -> dailyBriefHandler.handle(intent)
        is Intent.NextBrief -> nextBriefHandler.handle(intent)
        is Intent.PersonBrief -> personBriefHandler.handle(intent)
        is Intent.PromisesOwedMe -> promisesOwedMeHandler.handle(intent)
        is Intent.ActionsOpen -> actionsOpenHandler.handle(intent)
        is Intent.EodSummary -> eodSummaryHandler.handle(intent)
        is Intent.UndoLast -> undoLastHandler.handle(intent)
        is Intent.CorrectLast -> correctLastHandler.handle(intent)
    }
}
