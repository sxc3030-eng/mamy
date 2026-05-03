package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarAttendee
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a calendar event attendee to a `Person` row in the DB. Creates an `unmatched=true`
 * stub if no existing match by email. Skips `self` and `resource` attendees.
 *
 * NOTE: distinct from `com.mamy.android.domain.memory.PersonMatcher` (P4) which performs
 * fuzzy name matching on captures.
 */
@Singleton
class PersonMatcher @Inject constructor(
    private val personDao: PersonDao,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun matchOrCreate(attendee: CalendarAttendee): PersonEntity? {
        if (attendee.self == true || attendee.resource == true) return null
        val email = attendee.email?.takeIf { it.isNotBlank() }
        val displayName = attendee.displayName?.takeIf { it.isNotBlank() }
        if (email == null && displayName == null) return null

        if (email != null) {
            personDao.findByCalendarEmail(email)?.let { return it }
        }

        val resolvedName = displayName ?: email?.let { deriveNameFromEmail(it) } ?: return null
        val stub = PersonEntity(
            id = UUID.randomUUID(),
            name = resolvedName,
            email = email,
            roleHint = null,
            calendarAttendeeId = email,
            createdAt = clock.instant(),
            lastInteractionAt = null,
            interactionCount = 0,
            emotionalTrend = null,
            unmatched = true,
            archived = false
        )
        personDao.insert(stub)
        return stub
    }

    /**
     * "marc.tremblay@x.com" -> "Marc Tremblay"
     * "anais_brunet@x.com" -> "Anais Brunet"
     * "luc@x.com" -> "Luc"
     */
    private fun deriveNameFromEmail(email: String): String {
        val local = email.substringBefore('@')
        val parts = local.split('.', '_', '-').filter { it.isNotEmpty() }
        return parts.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
