package com.mamy.android.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * V1 stub : speak via [TextToSpeech] (same engine [TtsConfirmer] uses) and return null
 * from [listenOnce]. P5 will plumb listenOnce to the Whisper STT one-shot pipeline.
 */
@Singleton
class TextToSpeechAdapterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeechAdapter {

    @Volatile private var tts: TextToSpeech? = null

    override suspend fun speak(text: String, lang: Locale) {
        val engine = ensureInitialized() ?: return
        engine.language = lang
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "mamy-clarify")
    }

    override suspend fun listenOnce(timeoutMs: Long): String? {
        // P5 will wire to Whisper one-shot ; for P4 the clarifier still works
        // mechanically, just always returns null (caller falls back to "store unmatched").
        return null
    }

    private suspend fun ensureInitialized(): TextToSpeech? {
        tts?.let { return it }
        return suspendCancellableCoroutine { cont ->
            lateinit var engine: TextToSpeech
            engine = TextToSpeech(context) { status ->
                if (cont.isActive) {
                    if (status == TextToSpeech.SUCCESS) {
                        tts = engine
                        cont.resume(engine)
                    } else {
                        cont.resume(null)
                    }
                }
            }
        }
    }
}
