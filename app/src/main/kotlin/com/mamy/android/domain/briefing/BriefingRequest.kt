package com.mamy.android.domain.briefing

import java.time.Instant
import java.util.Locale

/**
 * Input to BriefingGenerator. `targetId` semantics depend on type:
 *  - DAILY        → null
 *  - PRE_MEETING  → Meeting.id (UUID as String)
 *  - PERSON_QUERY → Person.id (UUID as String)
 *  - EOD_SUMMARY  → null
 */
data class BriefingRequest(
    val type: BriefingType,
    val targetId: String?,
    val now: Instant,
    val locale: Locale,
)
