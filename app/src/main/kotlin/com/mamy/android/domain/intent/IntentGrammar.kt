package com.mamy.android.domain.intent

/**
 * Compiled regex table for the 10 voice intents (FR + EN).
 *
 * Patterns reference Annex A of the design spec (2026-05-02-mamy-design.md).
 * All patterns are anchored with `^MamY,?\s+` so they only fire when the wake-word
 * is the first token. Comma is optional (Whisper transcripts vary).
 *
 * Ordering matters in [IntentRouter] : PERSON_BRIEF must be tested BEFORE NEXT_BRIEF
 * because NEXT_BRIEF uses a negative lookahead `(?!\s+moi)` to avoid double-matching.
 */
object IntentGrammar {

    private val IGNORE = setOf(RegexOption.IGNORE_CASE)

    val CAPTURE: Regex = Regex(
        pattern = """^MamY,?\s+(prends|take a)\s+note\b""",
        options = IGNORE,
    )

    val DAILY_BRIEF: Regex = Regex(
        pattern = """^MamY,?\s+(ma journée|my day)\b""",
        options = IGNORE,
    )

    /** Matches "briefe" / "brief me" alone (NOT followed by "moi sur" or "me on"). */
    val NEXT_BRIEF: Regex = Regex(
        pattern = """^MamY,?\s+(briefe(?!-?\s*moi)|brief me)\s*$""",
        options = IGNORE,
    )

    /** "briefe-moi sur <X>" / "brief me on <X>" — captures name in group 2. */
    val PERSON_BRIEF_DIRECT: Regex = Regex(
        pattern = """^MamY,?\s+(briefe-?\s*moi sur|brief me on)\s+(.+?)\s*$""",
        options = IGNORE,
    )

    /** "c'est quoi avec <X>" / "what's up with <X>" — captures name in group 2. */
    val PERSON_BRIEF_ALIAS: Regex = Regex(
        pattern = """^MamY,?\s+(c'est quoi avec|what'?s up with)\s+(.+?)\s*$""",
        options = IGNORE,
    )

    val PROMISES_OWED_ME: Regex = Regex(
        pattern = """^MamY,?\s+(qui me devait quoi|what'?s owed to me)\b""",
        options = IGNORE,
    )

    val ACTIONS_OPEN: Regex = Regex(
        pattern = """^MamY,?\s+(mes actions ouvertes|my open actions)\b""",
        options = IGNORE,
    )

    val EOD_SUMMARY: Regex = Regex(
        pattern = """^MamY,?\s+(résume ma journée|summarize my day)\b""",
        options = IGNORE,
    )

    val UNDO_LAST: Regex = Regex(
        pattern = """^MamY,?\s+(oublie ça|forget that)\b""",
        options = IGNORE,
    )

    /** "modifie : <X>" / "edit: <X>" — captures correction in group 2. */
    val CORRECT_LAST: Regex = Regex(
        pattern = """^MamY,?\s+(modifie|edit)\s*:?\s*(.+?)\s*$""",
        options = IGNORE,
    )
}
