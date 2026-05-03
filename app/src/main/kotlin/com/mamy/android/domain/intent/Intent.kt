package com.mamy.android.domain.intent

/**
 * Result of intent classification by [IntentRouter].
 * Each variant is a typed payload routed to a dedicated handler by [IntentDispatcher].
 */
sealed class Intent {
    abstract val rawText: String

    data class Capture(override val rawText: String) : Intent()
    data class DailyBrief(override val rawText: String) : Intent()
    data class NextBrief(override val rawText: String) : Intent()
    data class PersonBrief(val personQuery: String, override val rawText: String) : Intent()
    data class PromisesOwedMe(override val rawText: String) : Intent()
    data class ActionsOpen(override val rawText: String) : Intent()
    data class EodSummary(override val rawText: String) : Intent()
    data class UndoLast(override val rawText: String) : Intent()
    data class CorrectLast(val correctedText: String, override val rawText: String) : Intent()
}

/**
 * Outcome of running a handler. Used both for unit tests and for the foreground service
 * to decide whether to chain a TTS speak call.
 */
data class IntentResult(
    val spokenText: String?,
    val success: Boolean,
    val error: String? = null,
) {
    companion object {
        fun spoken(text: String) = IntentResult(spokenText = text, success = true)
        fun silent() = IntentResult(spokenText = null, success = true)
        fun failure(error: String) = IntentResult(spokenText = null, success = false, error = error)
    }
}
