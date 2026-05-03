package com.mamy.android.domain.briefing

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.tts.TtsService
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles `Intent.PersonBrief` (« MamY, briefe-moi sur Marie »).
 * Resolves the person by name (accent-insensitive substring match).
 * If 0 → speak "personne inconnue". If >1 → speak clarification request
 * (handled inline; deeper disambiguation flow is P7+).
 *
 * Implements the existing `com.mamy.android.domain.intent.handler.PersonBriefHandler`
 * interface so it slots into the IntentDispatcher unchanged.
 */
@Singleton
class PersonQueryBriefHandler @Inject constructor(
    private val personDao: PersonDao,
    private val generator: BriefingGenerator,
    private val tts: TtsService,
    private val clock: Clock = Clock.systemUTC(),
) : com.mamy.android.domain.intent.handler.PersonBriefHandler {

    suspend fun run(personNameRaw: String, locale: Locale): IntentResult {
        val needle = personNameRaw.normalized()
        val all = personDao.allActive()
        val matches = all.filter { it.name.normalized().contains(needle) }
        return when (matches.size) {
            0 -> {
                val msg = if (locale.language == "fr")
                    "Je ne trouve personne du nom de $personNameRaw."
                else
                    "I can't find anyone named $personNameRaw."
                tts.speak(msg, locale, interrupt = true)
                IntentResult.Ok(msg)
            }
            1 -> {
                val req = BriefingRequest(
                    BriefingType.PERSON_QUERY, matches.first().id.toString(),
                    Instant.now(clock), locale,
                )
                val result = generator.generate(req)
                tts.speak(result.text, locale, interrupt = true)
                IntentResult.Ok(result.text)
            }
            else -> {
                val names = matches.joinToString(", ") { it.name }
                val msg = if (locale.language == "fr")
                    "Tu parles de qui ? J'ai trouvé : $names."
                else
                    "Who do you mean? I found: $names."
                tts.speak(msg, locale, interrupt = true)
                IntentResult.Ok(msg)
            }
        }
    }

    override suspend fun handle(intent: Intent.PersonBrief): IntentResult =
        run(intent.personQuery, Locale.getDefault())

    private fun String.normalized(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
}
