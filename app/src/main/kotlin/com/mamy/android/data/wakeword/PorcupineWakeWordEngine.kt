package com.mamy.android.data.wakeword

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Porcupine-backed wake-word engine.
 *
 * Resolution order at [start]:
 *  1. If a per-locale custom `.ppn` is bundled under
 *     `app/src/main/assets/wakeword/mamy_<lang>.ppn`, use it — Mamy responds
 *     to its own name. This is the V1.0 path (requires a Picovoice Console
 *     account to train the keyword).
 *  2. Otherwise fall back to [Porcupine.BuiltInKeyword.JARVIS] — the V1.5
 *     alpha path that ships without any `.ppn` and works out-of-the-box.
 *     Testers say "Jarvis".
 *
 * [WakeWordModelResolver.resolveKeywordPathOrNull] returns `null` rather than
 * throwing when the asset is missing, which keeps the engine usable in both
 * modes without code changes.
 */
@Singleton
class PorcupineWakeWordEngine @Inject constructor(
    private val context: Context,
    private val resolver: WakeWordModelResolver,
    private val accessKeyProvider: () -> String,
    private val localeProvider: () -> Locale = { Locale.getDefault() },
    private val fallbackKeyword: Porcupine.BuiltInKeyword = Porcupine.BuiltInKeyword.JARVIS,
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
        val customPath = resolver.resolveKeywordPathOrNull(localeProvider())
        val callback = PorcupineManagerCallback { _ -> listener.onWakeWordDetected() }

        val builder = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setSensitivity(sensitivity.porcupineFloat)
        if (customPath != null) {
            builder.setKeywordPath(customPath)
            Log.i(TAG, "Porcupine starting with custom keyword: $customPath")
        } else {
            builder.setKeyword(fallbackKeyword)
            Log.i(TAG, "Porcupine starting with built-in keyword: $fallbackKeyword")
        }
        manager = builder.build(context, callback).also { it.start() }
        running = true
        Log.i(TAG, "Porcupine started, sens=${sensitivity.porcupineFloat}")
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
