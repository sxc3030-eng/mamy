package com.mamy.android.domain.intent.handler

import com.mamy.android.domain.capture.LlmStructurer
import com.mamy.android.domain.capture.NoteWriter
import com.mamy.android.domain.capture.StructureOutcome
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter from P3's [LlmStructurer] + [NoteWriter] to the IntentHandler contract.
 *
 * Flow:
 *  1. structurer.structure(transcript, lang) → StructureOutcome
 *  2. writer.write(outcome, transcript, duration) → noteId
 *  3. tracker.record(noteId) so [UndoLastHandler] can find it within the 30 s window
 *
 * Non-default `language` lets P5 plumb the user's preferred locale ; in P4 the
 * service-layer wiring still uses [Lang.FR] so this default keeps the contract
 * additive.
 */
@Singleton
class CaptureHandler @Inject constructor(
    private val structurer: LlmStructurer,
    private val writer: NoteWriter,
    private val tracker: LastNoteTracker,
) : IntentHandler<Intent.Capture> {

    override suspend fun handle(intent: Intent.Capture): IntentResult =
        handle(intent, Lang.FR, durationSec = 0)

    suspend fun handle(intent: Intent.Capture, language: Lang, durationSec: Int): IntentResult {
        val outcome = structurer.structure(intent.rawText, language)
        val noteId = writer.write(outcome, intent.rawText, durationSec)
        return when (outcome) {
            is StructureOutcome.Success -> {
                noteId?.let { tracker.record(it) }
                IntentResult.spoken("Noté.")
            }
            is StructureOutcome.RawFallback -> {
                noteId?.let { tracker.record(it) }
                IntentResult.spoken("Noté en texte libre.")
            }
            is StructureOutcome.Failure -> IntentResult.failure(outcome.message)
        }
    }
}
