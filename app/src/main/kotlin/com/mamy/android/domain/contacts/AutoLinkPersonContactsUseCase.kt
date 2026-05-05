package com.mamy.android.domain.contacts

import com.mamy.android.data.contacts.Contact
import com.mamy.android.data.contacts.ContactsRepository
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background sync that bridges P5 calendar-matched [PersonEntity] rows to their
 * Android system [Contact] counterpart by `email == calendar_attendee_id`.
 *
 * Triggered by the caller (typically `MamYListenerService.onCreate` or a Worker)
 * whenever [ContactsRepository] re-emits. Idempotent — re-running on already-linked
 * Persons is a no-op.
 */
@Singleton
class AutoLinkPersonContactsUseCase @Inject constructor(
    private val personDao: PersonDao,
    private val contactsRepository: ContactsRepository,
) {

    /** Returns the number of Person rows newly linked. */
    suspend operator fun invoke(): Int {
        if (!contactsRepository.hasContactsPermission()) return 0
        val contacts = contactsRepository.loadContacts()
        if (contacts.isEmpty()) return 0

        // Build email → contactId index. Skip emails owned by 2+ contacts to avoid
        // wrong auto-links (manager and assistant sharing a shared mailbox, etc.).
        val emailToContactId: Map<String, String> = contacts
            .flatMap { c -> c.emails.map { it.lowercase() to c.id } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.distinct().size == 1 }
            .mapValues { it.value.first() }

        var linked = 0
        for (p in personDao.getAll()) {
            if (p.androidContactId != null) continue
            val key = (p.calendarAttendeeId ?: p.email)?.lowercase() ?: continue
            val contactId = emailToContactId[key] ?: continue
            personDao.update(p.copy(androidContactId = contactId))
            linked++
        }
        return linked
    }
}
