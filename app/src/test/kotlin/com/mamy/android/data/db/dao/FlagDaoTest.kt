package com.mamy.android.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.db.entity.FlagEntity
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
class FlagDaoTest {

    private lateinit var db: MamYDatabase
    private lateinit var flagDao: FlagDao
    private val pierreId = UUID.randomUUID()
    private lateinit var noteId: UUID

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MamYDatabase::class.java,
        ).allowMainThreadQueries().build()
        flagDao = db.flagDao()
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
    fun `getOpenByPerson returns unresolved flags`() = runTest {
        flagDao.insert(sampleFlag("demotivation", false))
        flagDao.insert(sampleFlag("burnout", true))
        val open = flagDao.getOpenByPerson(pierreId)
        assertEquals(1, open.size)
        assertEquals("demotivation", open[0].type)
    }

    @Test
    fun `markResolved sets resolved=true`() = runTest {
        val f = sampleFlag("conflict", false)
        flagDao.insert(f)
        flagDao.markResolved(f.id)
        assertTrue(flagDao.getById(f.id)!!.resolved)
    }

    private fun sampleFlag(type: String, resolved: Boolean) = FlagEntity(
        id = UUID.randomUUID(),
        personId = pierreId,
        type = type,
        source = "indirect:Marie",
        severity = "medium",
        note = "via Marie",
        resolved = resolved,
        fromNoteId = noteId,
        createdAt = Instant.now(),
    )
}
