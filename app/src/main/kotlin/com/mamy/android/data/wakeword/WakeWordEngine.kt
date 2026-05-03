package com.mamy.android.data.wakeword

/**
 * Continuous wake-word listener. Implementations own a background audio thread.
 * Lifecycle : [start] → idle on a callback thread → [stop] → [release].
 */
interface WakeWordEngine {
    fun start(sensitivity: WakeWordSensitivity, listener: WakeWordListener)
    fun stop()
    fun release()
    fun isRunning(): Boolean
}
