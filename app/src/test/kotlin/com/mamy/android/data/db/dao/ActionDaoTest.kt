package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class ActionDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var actionDao: ActionDao
    private lateinit var personId: UUID
    private lateinit var noteId: UUID

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        actionDao = db.actionDao()
        personId = UUID.randomUUID()
        noteId = UUID.randomUUID()
        db.personDao().insert(PersonEntity(
            id = personId, name = "Marie", email = "m@x.com", roleHint = null,
            calendarAttendeeId = null, createdAt = Instant.now(), lastInteractionAt = null,
            interactionCount = 0, emotionalTrend = null, unmatched = false, archived = false,
        ))
        db.noteDao().insert(NoteEntity(
            id = noteId, personId = personId, meetingId = null, rawText = "n",
            structuredJson = null, nonStructured = false, createdAt = Instant.now(),
            audioDurationSec = 0, llmProvider = "claude", llmCostCents = null,
        ))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert and getOpen lists only open actions`() = runTest {
        actionDao.insert(sampleAction("call David", "open"))
        actionDao.insert(sampleAction("done thing", "done"))
        val open = actionDao.getOpen()
        assertEquals(1, open.size)
        assertEquals("call David", open[0].description)
    }

    @Test
    fun `markDone sets status and done_at`() = runTest {
        val a = sampleAction("ping", "open")
        actionDao.insert(a)
        actionDao.markDone(a.id, Instant.parse("2026-05-02T20:00:00Z"))
        val updated = actionDao.getById(a.id)!!
        assertEquals("done", updated.status)
        assertTrue(updated.doneAt != null)
    }

    private fun sampleAction(desc: String, status: String) = ActionEntity(
        id = UUID.randomUUID(),
        description = desc,
        assignee = "self",
        linkedPersonId = personId,
        deadline = null,
        status = status,
        fromNoteId = noteId,
        createdAt = Instant.now(),
        doneAt = null,
    )
}
