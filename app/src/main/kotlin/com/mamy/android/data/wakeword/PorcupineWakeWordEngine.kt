package com.mamy.android.data.wakeword

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V1.5 alpha — uses Picovoice's built-in `JARVIS` keyword (no `.ppn` asset needed).
 * The custom `MamY` keyword path via [WakeWordModelResolver] is preserved in the
 * codebase for V1.0 when we own a Picovoice org and ship a per-locale `.ppn`.
 */
@Singleton
class PorcupineWakeWordEngine @Inject constructor(
    private val context: Context,
    private val accessKeyProvider: () -> String,
    private val keywordProvider: () -> Porcupine.BuiltInKeyword = { Porcupine.BuiltInKeyword.JARVIS },
) : WakeWordEngine {

    @Volatile private var manager: PorcupineManager? = null
    @Volatile private var running: Boolean = false

    override fun start(sensitivity: WakeWordSensitivity, listener: WakeWordListener) {
        if (running) {
            Log.w(TAG, "start() called while already running — ignoring")
            return
        }
        val accessKey = accessKeyProvider().also {
            require(it.isNotBlank()) { "Picovoice access key missing" }
        }
        val keyword = keywordProvider()
        val callback = PorcupineManagerCallback { _ -> listener.onWakeWordDetected() }

        manager = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setKeyword(keyword)
            .setSensitivity(sensitivity.porcupineFloat)
            .build(context, callback)
            .also { it.start() }
        running = true
        Log.i(TAG, "Porcupine started, keyword=$keyword sens=${sensitivity.porcupineFloat}")
    }

    override fun stop() {
        if (!running) return
        try {
            manager?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stop() failed", t)
        }
        running = false
        Log.i(TAG, "Porcupine stopped")
    }

    override fun release() {
        stop()
        try {
            manager?.delete()
        } catch (t: Throwable) {
            Log.w(TAG, "release() failed", t)
        }
        manager = null
    }

    override fun isRunning(): Boolean = running

    private companion object { const val TAG = "PorcupineEngine" }
}
