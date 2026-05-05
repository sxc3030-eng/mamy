package com.mamy.android.domain.contacts

import java.util.UUID

/**
 * P9 W1-E temporary contract used by [com.mamy.android.domain.intent.handler.TextToHandler]
 * until W1-D ships the real implementation in `data/contacts/`.
 *
 * The merge phase (orchestrator) is expected to replace [EmptyContactMatcher]
 * with W1-D's `data/contacts/ContactMatcher` at the Hilt binding level. The
 * domain-layer interface is the stable public surface (richer types live
 * close to the data layer that owns them).
 */
interface ContactMatcher {
    /**
     * Resolves a name uttered by the user into one of three outcomes :
     *  - [MatchResult.Single] : exactly one good match (priority Person team
     *    first, then exact contact, then substring, then fuzzy ≤ 2).
     *  - [MatchResult.Multiple] : 2+ ambiguous matches → caller routes to
     *    [com.mamy.android.domain.intent.handler.HomonymeClarifier].
     *  - [MatchResult.None] : no candidate found.
     */
    suspend fun findByName(query: String): MatchResult
}

/**
 * P9 W1-E minimal Contact-shape used by the SMS pipeline. Mirrors the fields
 * the orchestrator needs to drive [com.mamy.android.data.sms.SmsSender] and
 * persist the audit row.
 *
 * W1-D ships the richer `data/contacts/Contact` ; the merge can reconcile by
 * adding an extension that maps the data-layer type to this domain shape.
 */
data class ContactCandidate(
    val id: String?,
    val displayName: String,
    val phoneE164: String?,
    val linkedPersonId: UUID? = null,
)

sealed class MatchResult {
    data class Single(
        val candidate: ContactCandidate,
        val fromTeam: Boolean = false,
    ) : MatchResult()

    data class Multiple(val candidates: List<ContactCandidate>) : MatchResult()

    data object None : MatchResult()
}

/**
 * Stub bound by Hilt on the `wave1-sms` branch. Always returns [MatchResult.None]
 * so the orchestrator branch executes the "no contact found" path. The merge
 * with W1-D will swap this out for the real cascade matcher.
 */
class EmptyContactMatcher @javax.inject.Inject constructor() : ContactMatcher {
    override suspend fun findByName(query: String): MatchResult = MatchResult.None
}
