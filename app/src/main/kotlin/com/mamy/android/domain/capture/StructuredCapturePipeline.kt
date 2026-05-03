package com.mamy.android.domain.capture

import android.content.Context
import android.speech.tts.TextToSpeech
import com.mamy.android.data.tts.TtsConfirmer
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentDispatcher
import com.mamy.android.domain.intent.IntentRouter
import com.mamy.android.util.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * End-to-end orchestration of one captured transcript:
 * route → dispatch to handler → speak the IntentResult.
 *
 * P4 update : the pipeline now delegates ALL intents (not just Capture) to
 * [IntentDispatcher]. Capture still goes through [LlmStructurer] + [NoteWriter]
 * via [com.mamy.android.domain.intent.handler.CaptureHandler], so the prior P3
 * flow is preserved end-to-end ; non-Capture intents (briefings, memory queries)
 * now get a dedicated handler.
 *
 * Distinct from [CapturePipeline] (P2), which handles the audio→STT layer.
 * [StructuredCapturePipeline] is invoked AFTER P2 emits a TranscriptReady event.
 */
@Singleton
class StructuredCapturePipeline @Inject constructor(
    private val router: IntentRouter,
    private val structurer: LlmStructurer,
    private val writer: NoteWriter,
    private val tts: TtsConfirmer,
    private val dispatcher: IntentDispatcher,
    @ApplicationContext private val context: Context,
) {
    @Volatile private var ttsEngine: TextToSpeech? = null

    suspend fun handle(transcript: String, language: Lang, durationSec: Int) {
        val intent = router.classify(transcript)
        if (intent is Intent.Capture) {
            // Preserve P3 cascade flow + post-Capture audio confirmation.
            val outcome = structurer.structure(transcript, language)
            writer.write(outcome, transcript, durationSec)
            when (outcome) {
                is StructureOutcome.Success -> tts.confirm(outcome.note, language)
                is StructureOutcome.RawFallback -> tts.confirm(
                    com.mamy.android.data.llm.model.StructuredNote(),
                    language,
                )
                is StructureOutcome.Failure -> { /* no-op : silent on hard failure */ }
            }
            return
        }
        // Non-Capture intents : route through the dispatcher and speak the result.
        val result = dispatcher.dispatch(intent)
        result.spokenText?.let { speak(it, language) }
    }

    private suspend fun speak(text: String, lang: Lang) {
        val engine = ensureTts() ?: return
        engine.language = if (lang == Lang.FR) Locale.FRENCH else Locale.ENGLISH
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "mamy-intent")
    }

    private suspend fun ensureTts(): TextToSpeech? {
        ttsEngine?.let { return it }
        return suspendCancellableCoroutine { cont ->
            lateinit var engine: TextToSpeech
            engine = TextToSpeech(context) { status ->
                if (cont.isActive) {
                    if (status == TextToSpeech.SUCCESS) {
                        ttsEngine = engine
                        cont.resume(engine)
                    } else {
                        cont.resume(null)
                    }
                }
            }
        }
    }
}
