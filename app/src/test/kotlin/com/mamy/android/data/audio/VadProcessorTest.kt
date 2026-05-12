package com.mamy.android.data.audio

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VadProcessorTest {

    private fun frame(): ShortArray = ShortArray(AudioFormat.SAMPLES_PER_FRAME)

    private fun buildProcessor(decisions: List<Boolean>): VadProcessor {
        val proc = VadProcessor()
        val iterator = decisions.iterator()
        proc.vadFactory = {
            object : SimpleVad {
                override fun isSpeech(frame: ShortArray): Boolean =
                    if (iterator.hasNext()) iterator.next() else false
                override fun close() = Unit
            }
        }
        return proc
    }

    @Test
    fun `cuts after 3 dot 5 s of trailing silence post-speech`() = runTest {
        // v0.4.6 bumped SILENCE_CUT_MS from 1500 to 3500 → 3500/30 ≈ 117 frames.
        // 10 speech frames, then 120 silence frames → Captured.
        val decisions = List(10) { true } + List(120) { false }
        val proc = buildProcessor(decisions)
        val source = flow { repeat(decisions.size) { emit(frame()) } }

        val result = proc.captureUntilSilence(source)

        assertTrue(result is VadResult.Captured)
        // 10 speech frames * 480 samples = 4800
        assertEquals(4800, result.pcm.size)
    }

    @Test
    fun `aborts NoSpeech after 8 s of pure silence`() = runTest {
        // v0.4.6 bumped NO_SPEECH_ABORT_MS from 5000 to 8000 → 8000/30 ≈ 267 frames.
        val decisions = List(300) { false }
        val proc = buildProcessor(decisions)
        val source = flow { repeat(decisions.size) { emit(frame()) } }

        val result = proc.captureUntilSilence(source)

        assertTrue(result is VadResult.NoSpeech, "got $result")
    }

    @Test
    fun `enforces 90s hard cap as MaxDuration`() = runTest {
        // 3001 speech frames > MAX_SAMPLES / SAMPLES_PER_FRAME = 3000
        val decisions = List(3001) { true }
        val proc = buildProcessor(decisions)
        val source = flow { repeat(decisions.size) { emit(frame()) } }

        val result = proc.captureUntilSilence(source)

        assertTrue(result is VadResult.MaxDuration, "got $result")
        assertEquals(90, result.durationSec)
    }
}
