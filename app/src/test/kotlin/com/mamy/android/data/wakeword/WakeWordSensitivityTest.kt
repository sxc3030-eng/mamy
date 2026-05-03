package com.mamy.android.data.wakeword

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WakeWordSensitivityTest {

    @Test
    fun `LOW maps to 0_35`() {
        assertEquals(0.35f, WakeWordSensitivity.LOW.porcupineFloat, 0.0001f)
    }

    @Test
    fun `MEDIUM maps to 0_55 and is the default`() {
        assertEquals(0.55f, WakeWordSensitivity.MEDIUM.porcupineFloat, 0.0001f)
        assertEquals(WakeWordSensitivity.MEDIUM, WakeWordSensitivity.DEFAULT)
    }

    @Test
    fun `HIGH maps to 0_75`() {
        assertEquals(0.75f, WakeWordSensitivity.HIGH.porcupineFloat, 0.0001f)
    }
}
