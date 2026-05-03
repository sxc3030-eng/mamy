package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MeetingAttendeeDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var attendeeDao: MeetingAttendeeDao
    private lateinit var meetingId: UUID
    private lateinit var personA: UUID
    private lateinit var personB: UUID

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        attendeeDao = db.meetingAttendeeDao()
        meetingId = UUID.randomUUID()
        personA = UUID.randomUUID()
        personB = UUID.randomUUID()
        db.meetingDao().insert(MeetingEntity(
            id = meetingId, calendarEventId = null, title = "1:1",
            startsAt = Instant.now(), endsAt = Instant.now().plusSeconds(1800),
            briefingText = null, postNoteId = null, createdAt = Instant.now(),
        ))
        db.personDao().insert(PersonEntity(
            id = personA, name = "A", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.personDao().insert(PersonEntity(
            id = personB, name = "B", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getPersonsForMeeting returns linked persons`() = runTest {
        attendeeDao.insert(MeetingAttendeeEntity(meetingId, personA))
        attendeeDao.insert(MeetingAttendeeEntity(meetingId, personB))
        val ids = attendeeDao.getPersonIdsForMeeting(meetingId)
        assertEquals(2, ids.size)
    }

    @Test
    fun `getMeetingsForPerson returns all meetings`() = runTest {
        attendeeDao.insert(MeetingAttendeeEntity(meetingId, personA))
        val meetings = attendeeDao.getMeetingIdsForPerson(personA)
        assertEquals(1, meetings.size)
    }
}
