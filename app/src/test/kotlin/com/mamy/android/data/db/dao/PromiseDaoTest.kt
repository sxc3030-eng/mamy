package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.db.entity.PromiseEntity
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
class PromiseDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var promiseDao: PromiseDao
    private lateinit var noteId: UUID
    private val pierreId = UUID.randomUUID()

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        promiseDao = db.promiseDao()
        noteId = UUID.randomUUID()
        db.personDao().insert(PersonEntity(
            id = pierreId, name = "Pierre", email = null, roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.noteDao().insert(NoteEntity(
            id = noteId, personId = pierreId, meetingId = null, rawText = "n",
            structuredJson = null, nonStructured = false, createdAt = Instant.now(),
            audioDurationSec = 0, llmProvider = "claude", llmCostCents = null,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getOwedToMe returns active promises FROM others TO self`() = runTest {
        promiseDao.insert(samplePromise(from = pierreId.toString(), to = "self", "mockup", "active"))
        promiseDao.insert(samplePromise(from = "self", to = pierreId.toString(), "feedback", "active"))
        promiseDao.insert(samplePromise(from = pierreId.toString(), to = "self", "old", "kept"))
        val owed = promiseDao.getOwedToMe()
        assertEquals(1, owed.size)
        assertEquals("mockup", owed[0].what)
    }

    @Test
    fun `getOwedByMe returns active promises FROM self TO others`() = runTest {
        promiseDao.insert(samplePromise(from = "self", to = pierreId.toString(), "feedback", "active"))
        promiseDao.insert(samplePromise(from = pierreId.toString(), to = "self", "mockup", "active"))
        val owed = promiseDao.getOwedByMe()
        assertEquals(1, owed.size)
        assertEquals("feedback", owed[0].what)
    }

    private fun samplePromise(from: String, to: String, what: String, status: String) = PromiseEntity(
        id = UUID.randomUUID(),
        fromId = from,
        toId = to,
        what = what,
        due = null,
        status = status,
        fromNoteId = noteId,
        createdAt = Instant.now(),
        resolvedAt = null,
    )
}
