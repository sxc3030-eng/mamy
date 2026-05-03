package com.mamy.android.domain.intent.handler

import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubEodSummaryHandler @Inject constructor() : EodSummaryHandler {
    override suspend fun handle(intent: Intent.EodSummary): IntentResult =
        IntentResult.spoken("Le résumé de fin de journée n'est pas encore implémenté. EOD summary not yet implemented.")
}
