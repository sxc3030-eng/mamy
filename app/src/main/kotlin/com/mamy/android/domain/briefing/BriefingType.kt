package com.mamy.android.domain.briefing

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.ZERO

/**
 * Types of briefings MamY can produce.
 * `cacheTtl == ZERO` means "never cache, always generate fresh".
 */
enum class BriefingType(val cacheTtl: Duration, val maxSeconds: Int) {
    DAILY(cacheTtl = 8.hours, maxSeconds = 60),
    PRE_MEETING(cacheTtl = 1.hours, maxSeconds = 25),
    PERSON_QUERY(cacheTtl = ZERO, maxSeconds = 30),
    EOD_SUMMARY(cacheTtl = ZERO, maxSeconds = 60),
    ;

    val cached: Boolean get() = cacheTtl != ZERO
}
