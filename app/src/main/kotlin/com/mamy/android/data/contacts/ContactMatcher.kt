package com.mamy.android.data.contacts

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min

/**
 * Cascade contact-name resolver — see spec section 6.
 *
 * The cascade prefers the manager's *team* over random address-book entries:
 *
 *  1. PERSON exact match (P1 Person table — synced via Calendar P5).
 *     If the person has `androidContactId`, lift the linked Contact ; else synthesize one.
 *  2. CONTACT exact (case + accent normalized) on display/first/last name.
 *  3. CONTACT substring on display name.
 *  4. CONTACT Levenshtein fuzzy distance ≤ 2 on display/first name.
 *  5. None.
 *
 * Returns [MatchResult.Multiple] as soon as a single tier yields ≥2 hits — callers
 * (HomonymeClarifier P4) disambiguate.
 */
class ContactMatcher @Inject constructor(
    private val personDao: PersonDao,
    private val contactsRepository: ContactsRepository,
) {

    suspend fun findByName(query: String): MatchResult {
        val normalizedQuery = query.normalize()
        if (normalizedQuery.isEmpty()) return MatchResult.None

        val contacts = contactsRepository.loadContacts()

        // Tier 1 — Person table (the team). PersonDao.findByName already does a LIKE substring,
        // but we prefer exact-name matching here to honour the cascade ordering.
        val personHits = personDao.findByName(query)
            .filter { it.name.normalize() == normalizedQuery }
        when (personHits.size) {
            0 -> { /* fall through */ }
            1 -> return MatchResult.Single(
                contact = personHits[0].toContact(contacts),
                fromTeam = true,
            )
            else -> return MatchResult.Multiple(personHits.map { it.toContact(contacts) })
        }

        if (contacts.isEmpty()) return MatchResult.None

        // Tier 2 — Contact exact (display, first, last)
        val exact = contacts.filter { it.matchesExact(normalizedQuery) }
        when (exact.size) {
            0 -> { /* fall through */ }
            1 -> return MatchResult.Single(exact[0])
            else -> return MatchResult.Multiple(exact)
        }

        // Tier 3 — Contact substring (display name)
        val substring = contacts.filter { it.matchesSubstring(normalizedQuery) }
        when (substring.size) {
            0 -> { /* fall through */ }
            1 -> return MatchResult.Single(substring[0])
            else -> return MatchResult.Multiple(substring)
        }

        // Tier 4 — Levenshtein fuzzy ≤ 2 (display + first name)
        val fuzzy = contacts.filter { it.matchesFuzzy(normalizedQuery, maxDistance = 2) }
        return when (fuzzy.size) {
            0 -> MatchResult.None
            1 -> MatchResult.Single(fuzzy[0])
            else -> MatchResult.Multiple(fuzzy)
        }
    }

    /** Bridges a [PersonEntity] to the corresponding system [Contact] when linked, else synthesizes. */
    private fun PersonEntity.toContact(systemContacts: List<Contact>): Contact {
        val linked = androidContactId?.let { id -> systemContacts.firstOrNull { it.id == id } }
        if (linked != null) return linked
        return Contact(
            id = "person:$id",
            displayName = name,
            firstName = name.substringBefore(' ').takeIf { it.isNotBlank() },
            lastName = name.substringAfter(' ', missingDelimiterValue = "").takeIf { it.isNotBlank() },
            phones = emptyList(),
            emails = listOfNotNull(email, calendarAttendeeId).distinct(),
        )
    }
}

/** Lowercase + strip accents (NFD + diacritic removal) — visible for tests. */
internal fun String.normalize(): String {
    val nfd = Normalizer.normalize(this, Normalizer.Form.NFD)
    return nfd.replace(DIACRITICS, "").lowercase(Locale.ROOT).trim()
}

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

internal fun Contact.matchesExact(normalizedQuery: String): Boolean =
    displayName.normalize() == normalizedQuery ||
        firstName?.normalize() == normalizedQuery ||
        lastName?.normalize() == normalizedQuery

internal fun Contact.matchesSubstring(normalizedQuery: String): Boolean =
    displayName.normalize().contains(normalizedQuery)

internal fun Contact.matchesFuzzy(normalizedQuery: String, maxDistance: Int): Boolean {
    if (normalizedQuery.length < 3) return false // too short → false-positive prone (Jim/Tim)
    val display = displayName.normalize()
    if (levenshtein(display, normalizedQuery, maxDistance) <= maxDistance) return true
    val first = firstName?.normalize() ?: return false
    return levenshtein(first, normalizedQuery, maxDistance) <= maxDistance
}

/**
 * Bounded Levenshtein distance. Returns `maxDistance + 1` early when it's clear the bound
 * is exceeded — keeps the matcher snappy on 2k contacts.
 */
internal fun levenshtein(a: String, b: String, maxDistance: Int): Int {
    if (a == b) return 0
    if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1
    val rows = a.length + 1
    val cols = b.length + 1
    var prev = IntArray(cols) { it }
    var curr = IntArray(cols)
    for (i in 1 until rows) {
        curr[0] = i
        var rowMin = curr[0]
        for (j in 1 until cols) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = min(
                min(curr[j - 1] + 1, prev[j] + 1),
                prev[j - 1] + cost,
            )
            if (curr[j] < rowMin) rowMin = curr[j]
        }
        if (rowMin > maxDistance) return maxDistance + 1
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[cols - 1]
}
