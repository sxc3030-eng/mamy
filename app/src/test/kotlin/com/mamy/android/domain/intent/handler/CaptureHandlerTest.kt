package com.mamy.android.domain.intent.handler

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.domain.capture.LlmStructurer
import com.mamy.android.domain.capture.NoteWriter
import com.mamy.android.domain.capture.StructureOutcome
import com.mamy.android.domain.intent.Intent
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class CaptureHandlerTest {

    private val structurer: LlmStructurer = mockk()
    private val writer: NoteWriter = mockk()
    private val tracker = LastNoteTracker()
    private val handler = CaptureHandler(structurer, writer, tracker)

    @Test
    fun `success path persists note and records into tracker`() = runTest {
        val noteId = UUID.randomUUID()
        coEvery { structurer.structure(any(), Lang.FR) } returns StructureOutcome.Success(
            note = StructuredNote(),
            rawText = "raw",
            providerId = "claude",
            tokensIn = 10,
            tokensOut = 5,
        )
        coEvery { writer.write(any(), any(), any()) } returns noteId

        val result = handler.handle(Intent.Capture(
            rawText = "MamY, prends note Marie va mieux, faut appeler David",
        ))

        assertTrue(result.success)
        assertTrue(result.spokenText!!.startsWith("Noté"))
        coVerify { writer.write(any(), any(), any()) }
        assertNotNull(tracker.snapshot())
    }

    @Test
    fun `failure outcome returns error result without tracking`() = runTest {
        coEvery { structurer.structure(any(), Lang.FR) } returns StructureOutcome.Failure("network")
        coEvery { writer.write(any(), any(), any()) } returns null

        val result = handler.handle(Intent.Capture(rawText = "x"))

        assertTrue(!result.success)
    }
}
