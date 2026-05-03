package com.mamy.android.data.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioFormatTest {

    @Test
    fun `frame size = 480 samples (30 ms @ 16 kHz)`() {
        assertEquals(480, AudioFormat.SAMPLES_PER_FRAME)
    }

    @Test
    fun `frame bytes = 960`() {
        assertEquals(960, AudioFormat.BYTES_PER_FRAME)
    }

    @Test
    fun `max samples = 1_440_000 (90 s @ 16 kHz)`() {
        assertEquals(1_440_000, AudioFormat.MAX_SAMPLES)
    }
}
