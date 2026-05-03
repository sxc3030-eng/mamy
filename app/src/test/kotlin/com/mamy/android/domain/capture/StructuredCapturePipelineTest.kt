package com.mamy.android.domain.capture

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.tts.TtsConfirmer
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentRouter
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class StructuredCapturePipelineTest {

    private val router = mockk<IntentRouter>()
    private val structurer = mockk<LlmStructurer>()
    private val writer = mockk<NoteWriter>()
    private val tts = mockk<TtsConfirmer>(relaxed = true)
    private val pipeline = StructuredCapturePipeline(router, structurer, writer, tts)

    @Test
    fun `Capture intent runs structurer then writer then tts`() = runTest {
        val transcript = "Marie va mieux"
        val noteId = UUID.randomUUID()
        val outcome = StructureOutcome.Success(StructuredNote(), "{}", "claude", 100, 50)

        coEvery { router.route(transcript) } returns Intent.Capture(transcript)
        coEvery { structurer.structure(transcript, Lang.FR) } returns outcome
        coEvery { writer.write(outcome, transcript, 60) } returns noteId

        pipeline.handle(transcript = transcript, language = Lang.FR, durationSec = 60)

        coVerifyOrder {
            router.route(transcript)
            structurer.structure(transcript, Lang.FR)
            writer.write(outcome, transcript, 60)
            tts.confirm(any(), Lang.FR)
        }
    }

    @Test
    fun `non-Capture intent skips structurer`() = runTest {
        coEvery { router.route("MamY ma journée") } returns Intent.DailyBrief("MamY ma journée")

        pipeline.handle("MamY ma journée", Lang.FR, 5)

        coVerify(exactly = 0) { structurer.structure(any(), any()) }
        coVerify(exactly = 0) { writer.write(any(), any(), any()) }
    }

    @Test
    fun `Failure outcome triggers no TTS confirmation`() = runTest {
        val transcript = "x"
        val outcome = StructureOutcome.Failure("network down")

        coEvery { router.route(transcript) } returns Intent.Capture(transcript)
        coEvery { structurer.structure(transcript, Lang.EN) } returns outcome
        coEvery { writer.write(outcome, transcript, 30) } returns null

        pipeline.handle(transcript, Lang.EN, 30)

        coVerify(exactly = 0) { tts.confirm(any(), any()) }
    }
}
