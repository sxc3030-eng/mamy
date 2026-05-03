package com.mamy.android.data.wakeword

fun interface WakeWordListener {
    /** Called on background audio thread when "MamY" is detected. */
    fun onWakeWordDetected()
}
