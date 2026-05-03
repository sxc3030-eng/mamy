package com.mamy.android.domain.memory

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Substring-first match (handled by PersonDao.findByName SQL LIKE).
 * No Levenshtein in V1 — DAO does the heavy lifting; this layer adapts results
 * into a typed sealed class for handlers to dispatch on.
 */
@Singleton
class PersonMatcher @Inject constructor(
    private val dao: PersonDao,
) {
    sealed class MatchResult {
        data class SingleMatch(val person: PersonEntity) : MatchResult()
        data class Ambiguous(val candidates: List<PersonEntity>) : MatchResult()
        object NotFound : MatchResult()
    }

    suspend fun match(query: String): MatchResult {
        val rows = dao.findByName(query.trim())
        return when (rows.size) {
            0 -> MatchResult.NotFound
            1 -> MatchResult.SingleMatch(rows[0])
            else -> MatchResult.Ambiguous(rows)
        }
    }
}
