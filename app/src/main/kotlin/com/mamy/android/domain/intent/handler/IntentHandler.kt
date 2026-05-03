package com.mamy.android.domain.intent.handler

import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult

/**
 * Marker interface for all intent handlers. Each concrete handler binds to one
 * variant of [Intent] and returns an [IntentResult] consumed by the dispatcher.
 */
interface IntentHandler<I : Intent> {
    suspend fun handle(intent: I): IntentResult
}

// Briefing-flavor handlers — interfaces so P6 can wire LLM-backed impls.
interface DailyBriefHandler : IntentHandler<Intent.DailyBrief>
interface NextBriefHandler : IntentHandler<Intent.NextBrief>
interface PersonBriefHandler : IntentHandler<Intent.PersonBrief>
interface EodSummaryHandler : IntentHandler<Intent.EodSummary>
