package com.mamy.android.domain.capture

import com.mamy.android.data.db.dao.ActionDao
import com.mamy.android.data.db.dao.FlagDao
import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.dao.PromiseDao
import com.mamy.android.data.db.entity.ActionEntity
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.model.EmotionalState
import com.mamy.android.data.llm.model.FlagType
import com.mamy.android.data.llm.model.MeetingMeta
import com.mamy.android.data.llm.model.Severity
import com.mamy.android.data.llm.model.StructuredAction
import com.mamy.android.data.llm.model.StructuredFlag
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.llm.model.StructuredPerson
import com.mamy.android.data.llm.model.StructuredPromise
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteWriterTest {

    private val now = Instant.parse("2026-05-02T11:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val personDao = mockk<PersonDao>(relaxed = true)
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val actionDao = mockk<ActionDao>(relaxed = true)
    private val promiseDao = mockk<PromiseDao>(relaxed = true)
    private val flagDao = mockk<FlagDao>(relaxed = true)
    private val calculator = LlmCostCalculator()

    private val writer = NoteWriter(
        personDao = personDao,
        noteDao = noteDao,
        actionDao = actionDao,
        promiseDao = promiseDao,
        flagDao = flagDao,
        calculator = calculator,
        clock = clock,
    )

    @Test
    fun `Success outcome creates Person Note Action Promise Flag rows`() = runTest {
        coEvery { personDao.findByName(any()) } returns null

        val note = StructuredNote(
            persons = listOf(
                StructuredPerson(name = "Marie", emotionalState = EmotionalState.STRESSED, contextAdded = "projet X")
            ),
            actions = listOf(
                StructuredAction(description = "parler à David", assignee = "self", linkedPerson = "Marie")
            ),
            promises = listOf(
                StructuredPromise(from = "self", to = "Marie", what = "30 min CV review")
            ),
            flags = listOf(
                StructuredFlag(person = "Marie", type = FlagType.RISK, source = "direct", severity = Severity.LOW, note = "watch")
            ),
            meetingMeta = MeetingMeta(personMain = "Marie"),
        )

        val outcome = StructureOutcome.Success(
            note = note,
            rawText = "{}",
            providerId = "claude",
            tokensIn = 1000,
            tokensOut = 500,
        )

        val noteId = writer.write(outcome, transcript = "raw text", durationSec = 45)

        val personSlot = slot<PersonEntity>()
        coVerify { personDao.insert(capture(personSlot)) }                  // Marie creation (at minimum)
        val noteSlot = slot<NoteEntity>()
        coVerify { noteDao.insert(capture(noteSlot)) }
        assertEquals("raw text", noteSlot.captured.rawText)
        assertEquals("claude", noteSlot.captured.llmProvider)
        assertEquals(false, noteSlot.captured.nonStructured)
        // 1000 in + 500 out @ claude = 3500 microcents = 0 cents stored (truncated)
        assertEquals(0, noteSlot.captured.llmCostCents)

        val actionSlot = slot<ActionEntity>()
        coVerify { actionDao.insert(capture(actionSlot)) }
        assertEquals("parler à David", actionSlot.captured.description)
        assertEquals("self", actionSlot.captured.assignee)
        assertEquals("open", actionSlot.captured.status)

        coVerify { promiseDao.insert(any()) }
        coVerify { flagDao.insert(any()) }

        assertTrue(noteId.toString().isNotEmpty())
    }

    @Test
    fun `RawFallback outcome creates Note with non_structured=true and no children`() = runTest {
        val outcome = StructureOutcome.RawFallback(rawText = "garbage from llm", providerId = "claude")

        writer.write(outcome, transcript = "uh", durationSec = 10)

        val noteSlot = slot<NoteEntity>()
        coVerify { noteDao.insert(capture(noteSlot)) }
        assertEquals(true, noteSlot.captured.nonStructured)
        assertEquals("uh", noteSlot.captured.rawText)
        coVerify(exactly = 0) { actionDao.insert(any()) }
        coVerify(exactly = 0) { promiseDao.insert(any()) }
        coVerify(exactly = 0) { flagDao.insert(any()) }
    }

    @Test
    fun `Failure outcome writes nothing and returns null`() = runTest {
        val outcome = StructureOutcome.Failure(message = "no network")

        val noteId = writer.write(outcome, transcript = "x", durationSec = 5)

        org.junit.jupiter.api.Assertions.assertNull(noteId)
        coVerify(exactly = 0) { noteDao.insert(any()) }
    }
}
