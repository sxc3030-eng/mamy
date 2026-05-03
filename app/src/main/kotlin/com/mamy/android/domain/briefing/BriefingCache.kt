package com.mamy.android.domain.briefing

import com.mamy.android.data.db.dao.BriefingDao
import com.mamy.android.data.db.entity.BriefingEntity
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [BriefingDao] with TTL semantics. PERSON_QUERY and EOD_SUMMARY are
 * never cached: [get] returns null and [put] is a no-op for those types.
 *
 * `targetId == null` is normalized to the empty string for DB key purposes
 * (DAO `WHERE target_id = ?` matches that).
 */
@Singleton
class BriefingCache @Inject constructor(
    private val dao: BriefingDao,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun get(type: BriefingType, targetId: String?): BriefingResult? {
        if (!type.cached) return null
        val now = Instant.now(clock)
        val row = dao.fresh(type.name, targetId.orEmpty(), now) ?: return null
        return BriefingResult(
            text = row.text,
            generatedAt = row.generatedAt,
            expiresAt = row.expiresAt,
            cached = true,
            providerName = row.llmProvider,
            costCents = 0, // cached hits cost nothing this run
        )
    }

    suspend fun put(
        type: BriefingType,
        targetId: String?,
        text: String,
        providerName: String,
        costCents: Int,
    ): BriefingResult {
        val now = Instant.now(clock)
        val expires = now.plusSeconds(type.cacheTtl.inWholeSeconds)
        val result = BriefingResult(text, now, expires, cached = false, providerName, costCents)
        if (!type.cached) return result // skip DB write
        // Idempotency: nuke prior entries for (type, targetId) before insert.
        dao.deleteFor(type.name, targetId.orEmpty())
        dao.insert(BriefingEntity(
            id = UUID.randomUUID(),
            type = type.name,
            targetId = targetId.orEmpty(),
            generatedAt = now,
            expiresAt = expires,
            text = text,
            llmProvider = providerName,
            llmCostCents = costCents,
        ))
        return result
    }

    suspend fun evictExpired() {
        dao.deleteExpired(Instant.now(clock))
    }
}
