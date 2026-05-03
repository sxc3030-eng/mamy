package com.mamy.android.domain.intent.handler

import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returned by Hilt until P6 ships the LLM-backed impl.
 * Plays a vocal "not yet implemented" message so the user gets feedback.
 */
@Singleton
class StubDailyBriefHandler @Inject constructor() : DailyBriefHandler {
    override suspend fun handle(intent: Intent.DailyBrief): IntentResult =
        IntentResult.spoken("Le briefing matinal n'est pas encore implémenté. Daily briefing not yet implemented.")
}
