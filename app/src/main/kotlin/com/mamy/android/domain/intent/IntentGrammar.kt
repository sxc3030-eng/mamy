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

    // ----- P9 SMS text_to grammar (spec annex A) -----

    /**
     * "MamY texte à <who> que <body>", "MamY envoie un texto à <who> : <body>",
     * "MamY dis à <who> que <body>". Named groups : `who`, `body`.
     */
    val TEXT_TO_FR: Regex = Regex(
        pattern = "^MamY,?\\s+(?:texte|envoie\\s+(?:un\\s+)?(?:texto|sms)|dis)\\s+" +
            "(?:à\\s+)?(?<who>[\\w\\-'\\u00C0-\\u017F]+(?:\\s+[\\w\\-'\\u00C0-\\u017F]+)?)" +
            "\\s+(?:que\\s+|dis(?:-?lui)?\\s+|:\\s*|,\\s*)" +
            "(?<body>.+)\$",
        options = IGNORE,
    )

    /**
     * "MamY text <who> that <body>", "MamY send (a) text/sms to <who> : <body>",
     * "MamY text <who> saying <body>". Named groups : `who`, `body`.
     */
    val TEXT_TO_EN: Regex = Regex(
        pattern = "^MamY,?\\s+(?:text|send\\s+(?:a\\s+)?(?:text|sms)\\s+to)\\s+" +
            "(?:to\\s+)?(?<who>[\\w\\-']+(?:\\s+[\\w\\-']+)?)" +
            "\\s+(?:that\\s+|saying\\s+|:\\s*|,\\s*)" +
            "(?<body>.+)\$",
        options = IGNORE,
    )

    /**
     * Tries [TEXT_TO_FR] then [TEXT_TO_EN] against [transcript], returning a
     * (who, body) pair after sanitizing whitespace. Returns null on miss.
     *
     * Validation rules (post-extraction) :
     *  - `who`   : 1-50 chars
     *  - `body`  : >= 3 chars, <= 320 chars (cap at 2 SMS segments)
     */
    fun matchTextTo(transcript: String): Pair<String, String>? {
        val trimmed = transcript.trim()
        val match = TEXT_TO_FR.find(trimmed) ?: TEXT_TO_EN.find(trimmed) ?: return null
        val who = match.groups["who"]?.value?.trim().orEmpty()
        val body = match.groups["body"]?.value?.trim().orEmpty()
        if (who.isEmpty() || who.length > 50) return null
        if (body.length < 3 || body.length > 320) return null
        return who to body
    }
}
