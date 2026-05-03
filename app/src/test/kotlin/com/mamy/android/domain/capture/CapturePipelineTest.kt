package com.mamy.android.domain.capture

import app.cash.turbine.test
import com.mamy.android.data.audio.AudioCapture
import com.mamy.android.data.audio.AudioFormat
import com.mamy.android.data.audio.VadProcessor
import com.mamy.android.data.audio.VadResult
import com.mamy.android.data.stt.WhisperEngine
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentRouter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapturePipelineTest {

    private val audioCapture: AudioCapture = mockk()
    private val vad: VadProcessor = mockk()
    private val whisper: WhisperEngine = mockk()
    private val router = IntentRouter()

    @Test
    fun `emits Recording, Transcribing, TranscriptReady, Idle on happy path`() = runTest {
        val pcm = ShortArray(16_000) // 1 s
        every { audioCapture.frames() } returns emptyFlow()
        coEvery { vad.captureUntilSilence(any()) } returns VadResult.Captured(pcm, 1)
        coEvery { whisper.transcribe(pcm, "en") } returns Result.success("hello world")

        val pipeline = CapturePipeline(audioCapture, vad, whisper, router)

        pipeline.events.test {
            assertEquals(CaptureEvent.Idle, awaitItem())
            pipeline.runOneCapture("en")
            assertEquals(CaptureEvent.Recording, awaitItem())
            assertEquals(CaptureEvent.Transcribing, awaitItem())
            val tr = awaitItem()
            assertTrue(tr is CaptureEvent.TranscriptReady)
            tr as CaptureEvent.TranscriptReady
            assertEquals("hello world", tr.text)
            assertEquals(1, tr.durationSec)
            assertTrue(tr.intent is Intent.Capture)
            assertEquals(CaptureEvent.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits NoSpeech then Idle on silent capture`() = runTest {
        every { audioCapture.frames() } returns emptyFlow()
        coEvery { vad.captureUntilSilence(any()) } returns VadResult.NoSpeech(ShortArray(0), 0)

        val pipeline = CapturePipeline(audioCapture, vad, whisper, router)
        pipeline.events.test {
            assertEquals(CaptureEvent.Idle, awaitItem())
            pipeline.runOneCapture("en")
            assertEquals(CaptureEvent.Recording, awaitItem())
            assertEquals(CaptureEvent.NoSpeech, awaitItem())
            assertEquals(CaptureEvent.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Error on Whisper failure`() = runTest {
        val pcm = ShortArray(16_000)
        every { audioCapture.frames() } returns emptyFlow()
        coEvery { vad.captureUntilSilence(any()) } returns VadResult.Captured(pcm, 1)
        coEvery { whisper.transcribe(pcm, "en") } returns Result.failure(IllegalStateException("model"))

        val pipeline = CapturePipeline(audioCapture, vad, whisper, router)
        pipeline.events.test {
            assertEquals(CaptureEvent.Idle, awaitItem())
            pipeline.runOneCapture("en")
            assertEquals(CaptureEvent.Recording, awaitItem())
            assertEquals(CaptureEvent.Transcribing, awaitItem())
            val ev = awaitItem()
            assertTrue(ev is CaptureEvent.Error)
            assertEquals(CaptureEvent.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unused length sanity`() {
        assertEquals(480, AudioFormat.SAMPLES_PER_FRAME) // referenced types compile
    }
}
