package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.domain.intent.Intent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class UndoLastHandlerTest {

    private val noteDao: NoteDao = mockk(relaxed = true)
    private val actionDao: ActionDao = mockk(relaxed = true)
    private val promiseDao: PromiseDao = mockk(relaxed = true)
    private val flagDao: FlagDao = mockk(relaxed = true)
    private val tracker = LastNoteTracker()

    private val handler = UndoLastHandler(noteDao, actionDao, promiseDao, flagDao, tracker)

    @Test
    fun `success when within 30 sec window cascades delete`() = runTest {
        val noteId = UUID.randomUUID()
        val now = Instant.parse("2026-05-02T12:00:00Z")
        tracker.record(noteId, createdAt = now.minusSeconds(15))

        coEvery { actionDao.deleteByNoteId(noteId) } returns 2
        coEvery { promiseDao.deleteByNoteId(noteId) } returns 1
        coEvery { flagDao.deleteByNoteId(noteId) } returns 0
        coEvery { noteDao.deleteById(noteId) } returns 1

        val result = handler.handle(Intent.UndoLast("MamY, oublie ça"), now = now)

        assertTrue(result.success)
        assertEquals("Annulé.", result.spokenText)
        coVerify { actionDao.deleteByNoteId(noteId) }
        coVerify { promiseDao.deleteByNoteId(noteId) }
        coVerify { flagDao.deleteByNoteId(noteId) }
        coVerify { noteDao.deleteById(noteId) }
        assertEquals(null, tracker.snapshot(now = now))
    }

    @Test
    fun `expired window returns failure-friendly message`() = runTest {
        val noteId = UUID.randomUUID()
        val now = Instant.parse("2026-05-02T12:00:00Z")
        tracker.record(noteId, createdAt = now.minusSeconds(60))

        val result = handler.handle(Intent.UndoLast("MamY, oublie ça"), now = now)

        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("trop tard", ignoreCase = true))
        coVerify(exactly = 0) { noteDao.deleteById(any()) }
    }

    @Test
    fun `no recent note returns informative message`() = runTest {
        val result = handler.handle(Intent.UndoLast("MamY, oublie ça"))
        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("rien à annuler", ignoreCase = true) ||
                   result.spokenText!!.contains("aucune", ignoreCase = true))
    }
}
