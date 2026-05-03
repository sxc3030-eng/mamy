package com.mamy.android.domain.memory

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class ConfirmPersonStubUseCase @Inject constructor(
    private val personDao: PersonDao
) {

    fun observeUnmatched(): Flow<List<PersonEntity>> = personDao.observeUnmatched()

    suspend fun confirm(personId: UUID) {
        val current = personDao.findById(personId) ?: return
        personDao.update(current.copy(unmatched = false))
    }

    suspend fun editName(personId: UUID, newName: String) {
        val current = personDao.findById(personId) ?: return
        personDao.update(current.copy(name = newName, unmatched = false))
    }

    /**
     * Merges [stubId] into [targetId]: target inherits the calendar_attendee_id, stub is archived.
     * Future calendar events with that attendee email will resolve to the target person.
     * NOTE: meeting_attendee rows pointing at stubId are NOT rewritten in V1 - they stay historical.
     * V2 may add an attendee-rewrite migration use-case.
     */
    suspend fun mergeInto(stubId: UUID, targetId: UUID) {
        val stub = personDao.findById(stubId) ?: return
        val target = personDao.findById(targetId) ?: return
        if (stub.calendarAttendeeId != null) {
            personDao.update(target.copy(calendarAttendeeId = stub.calendarAttendeeId))
        }
        personDao.update(stub.copy(archived = true, unmatched = false))
    }
}
