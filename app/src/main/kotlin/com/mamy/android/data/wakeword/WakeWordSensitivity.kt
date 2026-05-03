package com.mamy.android.data.wakeword

/**
 * User-tunable wake-word sensitivity.
 * Maps to Picovoice Porcupine sensitivity float (0.0 strict … 1.0 permissive).
 */
enum class WakeWordSensitivity(val porcupineFloat: Float) {
    LOW(0.35f),
    MEDIUM(0.55f),
    HIGH(0.75f);

    companion object {
        val DEFAULT = MEDIUM
    }
}
