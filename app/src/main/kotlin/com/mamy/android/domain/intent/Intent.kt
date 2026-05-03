package com.mamy.android.domain.intent

/** All intents recognized by the voice grammar. P2 only emits [CAPTURE]. */
sealed interface Intent {
    data class Capture(val rawText: String) : Intent
    data object DailyBrief : Intent
    data object NextBrief : Intent
    data class PersonBrief(val person: String) : Intent
    data object PromisesOwedMe : Intent
    data object ActionsOpen : Intent
    data object EodSummary : Intent
    data object UndoLast : Intent
    data class CorrectLast(val correction: String) : Intent
}
