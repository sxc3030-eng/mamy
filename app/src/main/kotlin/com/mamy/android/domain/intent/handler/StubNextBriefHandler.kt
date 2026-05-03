package com.mamy.android.domain.intent.handler

import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubNextBriefHandler @Inject constructor() : NextBriefHandler {
    override suspend fun handle(intent: Intent.NextBrief): IntentResult =
        IntentResult.spoken("Le briefing pré-meeting n'est pas encore implémenté. Pre-meeting briefing not yet implemented.")
}
