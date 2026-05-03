package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.data.db.entity.NoteEntity
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.domain.capture.LlmStructurer
import com.mamy.android.domain.capture.StructureOutcome
import com.mamy.android.domain.intent.Intent
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertTrue

class CorrectLastHandlerTest {

    private val noteDao: NoteDao = mockk(relaxed = true)
    private val structurer: LlmStructurer = mockk()
    private val handler = CorrectLastHandler(noteDao, structurer)

    @Test
    fun `re-submits combined transcript and updates note`() = runTest {
        val original = NoteEntity(
            id = UUID.randomUUID(),
            personId = null,
            meetingId = null,
            rawText = "Marie va mieux RH a confirmé",
            structuredJson = """{"persons":[{"name":"Marie"}]}""",
            nonStructured = false,
            createdAt = Instant.now(),
            audioDurationSec = 30,
            llmProvider = "claude",
            llmCostCents = 1,
        )
        coEvery { noteDao.findLatest() } returns original
        coEvery { structurer.structure(any(), Lang.FR) } returns StructureOutcome.Success(
            note = StructuredNote(),
            rawText = """{"persons":[{"name":"Pierre"}]}""",
            providerId = "claude",
            tokensIn = 100,
            tokensOut = 50,
        )

        val result = handler.handle(Intent.CorrectLast(
            correctedText = "remplace Marie par Pierre",
            rawText = "MamY, modifie : remplace Marie par Pierre",
        ))

        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("Corrigé") || result.spokenText!!.contains("Updated"))

        val captured = slot<String>()
        coVerify {
            structurer.structure(capture(captured), Lang.FR)
        }
        assertTrue(captured.captured.contains("Marie va mieux"))
        assertTrue(captured.captured.contains("remplace Marie par Pierre"))
        coVerify { noteDao.update(any()) }
    }

    @Test
    fun `no recent note returns informative message`() = runTest {
        coEvery { noteDao.findLatest() } returns null
        val result = handler.handle(Intent.CorrectLast(
            correctedText = "x",
            rawText = "MamY, modifie : x",
        ))
        assertTrue(result.success)
        assertTrue(result.spokenText!!.contains("aucune", ignoreCase = true) ||
                   result.spokenText!!.contains("rien", ignoreCase = true))
    }
}
