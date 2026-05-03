package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.dao.NoteDao
import com.mamy.android.domain.capture.LlmStructurer
import com.mamy.android.domain.capture.StructureOutcome
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-runs the LLM structurer on the previous transcript concatenated with the
 * user's correction directive, then overwrites the previous Note's structured_json.
 *
 * NOTE: cascading Actions/Promises/Flags from the original Note are NOT auto-rebuilt
 * here. The user can chain `oublie ça` before `modifie :` if they want a clean redo.
 * V1 limitation, documented in spec section 5.
 *
 * Adapter notes for P3-shipped LlmStructurer :
 *  - the real signature is `structure(transcript: String, language: Lang): StructureOutcome`
 *  - we always pass [Lang.FR] (P5 will plumb language from settings)
 *  - StructureOutcome.Success carries provider id + token counts ; we record provider only
 *  - on RawFallback / Failure we keep the existing Note unchanged and tell the user
 */
@Singleton
class CorrectLastHandler @Inject constructor(
    private val noteDao: NoteDao,
    private val structurer: LlmStructurer,
) : IntentHandler<Intent.CorrectLast> {

    override suspend fun handle(intent: Intent.CorrectLast): IntentResult {
        val last = noteDao.findLatest()
            ?: return IntentResult.spoken("Aucune capture récente à corriger. No recent capture to correct.")

        val combined = buildString {
            append("Original transcript: ")
            append(last.rawText)
            append("\n\nUser correction: ")
            append(intent.correctedText)
            append("\n\nApply the correction and re-emit the JSON.")
        }

        return when (val outcome = structurer.structure(combined, Lang.FR)) {
            is StructureOutcome.Success -> {
                noteDao.update(
                    last.copy(
                        structuredJson = serialize(outcome),
                        nonStructured = false,
                        llmProvider = outcome.providerId,
                    )
                )
                IntentResult.spoken("Corrigé. Updated.")
            }
            is StructureOutcome.RawFallback -> {
                noteDao.update(
                    last.copy(
                        rawText = outcome.rawText,
                        nonStructured = true,
                        llmProvider = outcome.providerId,
                    )
                )
                IntentResult.spoken("Correction appliquée mais non structurée.")
            }
            is StructureOutcome.Failure -> {
                IntentResult.spoken("Échec de la correction. ${outcome.message}")
            }
        }
    }

    private fun serialize(s: StructureOutcome.Success): String =
        // Minimal serialization — full JSON re-encode is a P5 task once settings carry the formatter
        s.rawText
}
