package com.mamy.android.domain.capture

import com.mamy.android.data.tts.TtsConfirmer
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentRouter
import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton

/**
 * End-to-end orchestration of one captured transcript:
 * route → (if CAPTURE) structure → write → TTS confirm.
 *
 * Other intents (DailyBrief, NextBrief, ...) are out of scope for P3 and
 * delegated to handlers that arrive in P5/P6.
 *
 * Distinct from [CapturePipeline] (P2), which handles the audio→STT layer.
 * P3 [StructuredCapturePipeline] is invoked AFTER P2 emits a TranscriptReady event.
 */
@Singleton
class StructuredCapturePipeline @Inject constructor(
    private val router: IntentRouter,
    private val structurer: LlmStructurer,
    private val writer: NoteWriter,
    private val tts: TtsConfirmer,
) {
    suspend fun handle(transcript: String, language: Lang, durationSec: Int) {
        val intent = router.route(transcript)
        if (intent !is Intent.Capture) return

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
    }
}
