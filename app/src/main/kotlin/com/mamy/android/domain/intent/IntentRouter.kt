package com.mamy.android.domain.intent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a Whisper transcript to a typed [Intent].
 *
 * Pattern ordering :
 *  1. PERSON_BRIEF (direct + alias) — must precede NEXT_BRIEF
 *  2. NEXT_BRIEF
 *  3. CORRECT_LAST — must precede CAPTURE (both can swallow free-form tails)
 *  4. CAPTURE
 *  5. DAILY_BRIEF / PROMISES_OWED_ME / ACTIONS_OPEN / EOD_SUMMARY / UNDO_LAST
 *
 * Falls back to [Intent.Capture] when no pattern matches.
 */
@Singleton
class IntentRouter @Inject constructor() {

    fun classify(transcript: String): Intent {
        val trimmed = transcript.trim()

        // P9 — text_to (must precede CAPTURE since "texte à" doesn't overlap with
        // "prends note" but the regex is specific so we route it early).
        IntentGrammar.matchTextTo(trimmed)?.let { (who, body) ->
            return Intent.TextTo(who = who, body = body, rawText = trimmed)
        }

        // Person brief variants (must beat next_brief)
        IntentGrammar.PERSON_BRIEF_DIRECT.find(trimmed)?.let { match ->
            return Intent.PersonBrief(
                personQuery = match.groupValues[2].trim(),
                rawText = trimmed,
            )
        }
        IntentGrammar.PERSON_BRIEF_ALIAS.find(trimmed)?.let { match ->
            return Intent.PersonBrief(
                personQuery = match.groupValues[2].trim(),
                rawText = trimmed,
            )
        }

        // Next brief
        if (IntentGrammar.NEXT_BRIEF.containsMatchIn(trimmed)) {
            return Intent.NextBrief(rawText = trimmed)
        }

        // Correction (must beat CAPTURE — "modifie" is more specific)
        IntentGrammar.CORRECT_LAST.find(trimmed)?.let { match ->
            return Intent.CorrectLast(
                correctedText = match.groupValues[2].trim(),
                rawText = trimmed,
            )
        }

        // Capture
        if (IntentGrammar.CAPTURE.containsMatchIn(trimmed)) {
            return Intent.Capture(rawText = trimmed)
        }

        // Single-shot intents
        if (IntentGrammar.DAILY_BRIEF.containsMatchIn(trimmed)) {
            return Intent.DailyBrief(rawText = trimmed)
        }
        if (IntentGrammar.PROMISES_OWED_ME.containsMatchIn(trimmed)) {
            return Intent.PromisesOwedMe(rawText = trimmed)
        }
        if (IntentGrammar.ACTIONS_OPEN.containsMatchIn(trimmed)) {
            return Intent.ActionsOpen(rawText = trimmed)
        }
        if (IntentGrammar.EOD_SUMMARY.containsMatchIn(trimmed)) {
            return Intent.EodSummary(rawText = trimmed)
        }
        if (IntentGrammar.UNDO_LAST.containsMatchIn(trimmed)) {
            return Intent.UndoLast(rawText = trimmed)
        }

        // Fallback
        return Intent.Capture(rawText = trimmed)
    }

    /** Backwards-compatible alias kept until P3 callers migrate to [classify]. */
    fun route(transcript: String): Intent = classify(transcript)
}
