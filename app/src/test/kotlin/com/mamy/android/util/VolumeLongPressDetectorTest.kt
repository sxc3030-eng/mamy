package com.mamy.android.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumeLongPressDetectorTest {

    private val volUp = 24 // KeyEvent.KEYCODE_VOLUME_UP

    @Test
    fun `fires after 1000 ms of repeats`() {
        var fakeTime = 0L
        var fired = 0
        val det = VolumeLongPressDetector(
            thresholdMs = 1000,
            onLongPress = { fired++ },
            now = { fakeTime },
        )

        assertFalse(det.onKeyDown(volUp))           // start, t=0
        fakeTime = 500
        assertFalse(det.onKeyDown(volUp))           // still held, t=500
        fakeTime = 1001
        assertTrue(det.onKeyDown(volUp))            // crossed threshold
        assertEquals(1, fired)
    }

    @Test
    fun `does not fire if released before threshold`() {
        var fakeTime = 0L
        var fired = 0
        val det = VolumeLongPressDetector(1000, { fired++ }, { fakeTime })
        det.onKeyDown(volUp)
        fakeTime = 800
        det.onKeyUp(volUp)
        fakeTime = 1500
        det.onKeyDown(volUp) // new press
        assertEquals(0, fired)
    }

    @Test
    fun `fires only once per press`() {
        var fakeTime = 0L
        var fired = 0
        val det = VolumeLongPressDetector(1000, { fired++ }, { fakeTime })
        det.onKeyDown(volUp); fakeTime = 1100
        det.onKeyDown(volUp) // fires
        fakeTime = 1500
        det.onKeyDown(volUp) // does NOT re-fire
        assertEquals(1, fired)
    }
}
