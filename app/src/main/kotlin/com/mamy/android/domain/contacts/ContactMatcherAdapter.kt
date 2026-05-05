package com.mamy.android.domain.contacts

import com.mamy.android.data.contacts.PhoneType
import javax.inject.Inject
import javax.inject.Singleton
import com.mamy.android.data.contacts.ContactMatcher as DataContactMatcher
import com.mamy.android.data.contacts.MatchResult as DataMatchResult

/**
 * Bridges W1-D's data-layer cascade matcher (`data.contacts.ContactMatcher`)
 * to W1-E's domain-layer interface (`domain.contacts.ContactMatcher`) used by
 * `TextToHandler`. The adapter :
 *
 *  - delegates name resolution to the cascade
 *  - picks the best phone (priority MOBILE > WORK > HOME > OTHER, first match)
 *  - converts the rich data-layer `Contact` to the slim domain `ContactCandidate`
 *
 * If no phone is found on the contact, the candidate is still returned with
 * `phoneE164 = null` — `TextToHandler` short-circuits with the "no phone"
 * vocal feedback per the design spec.
 */
@Singleton
class ContactMatcherAdapter @Inject constructor(
    private val dataMatcher: DataContactMatcher,
) : ContactMatcher {

    override suspend fun findByName(query: String): MatchResult {
        return when (val result = dataMatcher.findByName(query)) {
            is DataMatchResult.None -> MatchResult.None
            is DataMatchResult.Single -> MatchResult.Single(
                candidate = result.contact.toCandidate(),
                fromTeam = result.fromTeam,
            )
            is DataMatchResult.Multiple -> MatchResult.Multiple(
                candidates = result.contacts.map { it.toCandidate() },
            )
        }
    }

    private fun com.mamy.android.data.contacts.Contact.toCandidate(): ContactCandidate {
        val phone = phones
            .sortedBy { phoneTypePriority(it.type) }
            .firstOrNull()
            ?.e164
        return ContactCandidate(
            id = id,
            displayName = displayName,
            phoneE164 = phone,
            linkedPersonId = null, // PersonEntity link resolved upstream by ContactMatcher cascade tier 1
        )
    }

    private fun phoneTypePriority(type: PhoneType): Int = when (type) {
        PhoneType.MOBILE -> 0
        PhoneType.WORK -> 1
        PhoneType.HOME -> 2
        PhoneType.OTHER -> 3
    }
}
