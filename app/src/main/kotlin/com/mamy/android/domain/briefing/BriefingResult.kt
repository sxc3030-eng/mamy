package com.mamy.android.domain.briefing

import java.time.Instant

/**
 * Output of BriefingGenerator. `cached==true` → no LLM call was issued
 * for this run, `costCents` will be 0, `providerName` is the value persisted
 * when the briefing was first produced.
 */
data class BriefingResult(
    val text: String,
    val generatedAt: Instant,
    val expiresAt: Instant,
    val cached: Boolean,
    val providerName: String,
    val costCents: Int,
)
