package com.mamy.android.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [TextToSpeech] supporting :
 *  - FR / EN voice switching by Locale
 *  - sequential queue (FIFO unless interrupt=true flushes it)
 *  - interrupt() : stops speech immediately
 *  - speed knob (0.5–2.0) read from settings via setRate()
 *
 * Suspends until utterance completes (or interrupted), so callers can
 * sequence with `.also { tts.speak(...) }`. Tests use Robolectric to
 * exercise the lifecycle.
 *
 * Independent of [TtsConfirmer] (P3 short post-capture confirmations) :
 * each owns its own [TextToSpeech] instance to avoid race conditions on
 * `setLanguage`/`speak` when both are active.
 */
@Singleton
class TtsService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ready = CompletableDeferred<TextToSpeech>()
    private val initFailed = AtomicBoolean(false)
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var rate: Float = 1.0f

    init {
        scope.launch { initialize() }
    }

    private fun initialize() {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = textToSpeech
                if (engine != null) {
                    ready.complete(engine)
                } else {
                    initFailed.set(true)
                    ready.completeExceptionally(IllegalStateException("TTS init: engine reference lost"))
                }
            } else {
                initFailed.set(true)
                ready.completeExceptionally(IllegalStateException("TTS init failed: status=$status"))
            }
        }
        textToSpeech = tts
    }

    @Volatile private var textToSpeech: TextToSpeech? = null

    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2.0f)
        textToSpeech?.setSpeechRate(rate)
    }

    /**
     * Speak [text] in the voice matching [locale]. If [interrupt] is true,
     * any in-flight utterance is stopped first. Suspends until completion.
     * If TTS init failed, returns silently.
     */
    suspend fun speak(text: String, locale: Locale, interrupt: Boolean = false) {
        if (initFailed.get()) return
        val tts = try {
            ready.await()
        } catch (e: IllegalStateException) {
            return
        }
        withContext(Dispatchers.Main) {
            tts.language = pickLanguage(tts, locale)
            tts.setSpeechRate(rate)
        }
        val done = CompletableDeferred<Unit>()
        val id = "u-${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.complete(Unit)
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.complete(Unit)
            }
        })
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(text, mode, null, id)
        done.await()
    }

    fun interrupt() { textToSpeech?.stop() }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun pickLanguage(tts: TextToSpeech, locale: Locale): Locale {
        val asked = if (locale.language in setOf("fr", "en")) locale else Locale.ENGLISH
        return when (tts.isLanguageAvailable(asked)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> asked
            else -> Locale.ENGLISH
        }
    }
}
