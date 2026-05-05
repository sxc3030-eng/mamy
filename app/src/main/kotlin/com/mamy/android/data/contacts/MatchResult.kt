package com.mamy.android.data.contacts

/**
 * Outcome of [ContactMatcher.findByName].
 *
 *  - [Single]   = unambiguous hit. `fromTeam=true` when matched via the [PersonEntity] table
 *                 (the manager's known reports), giving them priority over generic contacts.
 *  - [Multiple] = 2+ hits at the same cascade tier — caller (HomonymeClarifier P4) disambiguates.
 *  - [None]     = nothing matched at any tier.
 */
sealed class MatchResult {
    data class Single(val contact: Contact, val fromTeam: Boolean = false) : MatchResult()
    data class Multiple(val contacts: List<Contact>) : MatchResult()
    data object None : MatchResult()
}
