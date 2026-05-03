package com.mamy.android.domain.intent.handler

import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.tts.TextToSpeechAdapter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves homonymes via TTS round-trip when 2+ Person rows match a name query
 * AND no active calendar event narrows it down.
 *
 * Strategy : speak "Tu parles de <A> ou <B> ?", listen up to 5 sec, match
 * the response against last-name tokens of each candidate. First substring hit wins.
 * Returns null if no match — caller decides whether to retry or store as unmatched.
 */
@Singleton
class HomonymeClarifier @Inject constructor(
    private val tts: TextToSpeechAdapter,
) {
    suspend fun disambiguate(
        candidates: List<PersonEntity>,
        lang: Locale,
        listenTimeoutMs: Long = 5_000L,
    ): PersonEntity? {
        require(candidates.size >= 2) { "disambiguate requires 2+ candidates" }
        val question = if (lang.language == "fr") {
            "Tu parles de " + candidates.joinToString(" ou ") { it.name } + " ?"
        } else {
            "Did you mean " + candidates.joinToString(" or ") { it.name } + "?"
        }
        tts.speak(question, lang)
        val response = tts.listenOnce(listenTimeoutMs) ?: return null
        val needle = response.lowercase()
        // Match by any token of the candidate name (typically last name disambiguates)
        return candidates.firstOrNull { p ->
            p.name.split(" ").any { tok -> tok.length >= 3 && needle.contains(tok.lowercase()) }
        }
    }
}
