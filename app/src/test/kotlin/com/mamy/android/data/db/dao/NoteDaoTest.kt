package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var personDao: PersonDao
    private lateinit var person: PersonEntity

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        noteDao = db.noteDao()
        personDao = db.personDao()
        person = PersonEntity(
            id = UUID.randomUUID(), name = "Marie", email = "m@x.com",
            roleHint = null, calendarAttendeeId = null, createdAt = Instant.now(),
            lastInteractionAt = null, interactionCount = 0, emotionalTrend = null,
            unmatched = false, archived = false,
        )
        personDao.insert(person)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getById round-trips`() = runTest {
        val n = sampleNote(person.id, "raw text")
        noteDao.insert(n)
        val fetched = noteDao.getById(n.id)
        assertNotNull(fetched)
        assertEquals("raw text", fetched!!.rawText)
    }

    @Test
    fun `getByPerson returns notes ordered desc`() = runTest {
        noteDao.insert(sampleNote(person.id, "old", Instant.parse("2026-05-01T08:00:00Z")))
        noteDao.insert(sampleNote(person.id, "new", Instant.parse("2026-05-02T08:00:00Z")))
        val list = noteDao.getByPersonOrderedDesc(person.id)
        assertEquals(2, list.size)
        assertEquals("new", list[0].rawText)
    }

    @Test
    fun `getNonStructuredNotes only returns flagged ones`() = runTest {
        noteDao.insert(sampleNote(person.id, "ok", nonStructured = false))
        noteDao.insert(sampleNote(person.id, "broken", nonStructured = true))
        val flagged = noteDao.getNonStructured()
        assertEquals(1, flagged.size)
        assertTrue(flagged[0].nonStructured)
    }

    private fun sampleNote(
        personId: UUID,
        rawText: String,
        createdAt: Instant = Instant.now(),
        nonStructured: Boolean = false,
    ) = NoteEntity(
        id = UUID.randomUUID(),
        personId = personId,
        meetingId = null,
        rawText = rawText,
        structuredJson = null,
        nonStructured = nonStructured,
        createdAt = createdAt,
        audioDurationSec = 30,
        llmProvider = "claude",
        llmCostCents = 1,
    )
}
