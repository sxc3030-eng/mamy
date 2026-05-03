package com.mamy.android.domain.briefing

import com.mamy.android.data.calendar.CalendarRepository
import com.mamy.android.data.tts.TtsService
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles `Intent.NextBrief` (« MamY, briefe »).
 * Picks the closest upcoming meeting (≤30 min) and briefs the user about it.
 * If no upcoming meeting, speaks a short fallback.
 *
 * Implements the existing `com.mamy.android.domain.intent.handler.NextBriefHandler`
 * interface so it slots into the IntentDispatcher unchanged.
 */
@Singleton
class PreMeetingBriefHandler @Inject constructor(
    private val calendar: CalendarRepository,
    private val generator: BriefingGenerator,
    private val tts: TtsService,
    private val clock: Clock = Clock.systemUTC(),
) : com.mamy.android.domain.intent.handler.NextBriefHandler {
    suspend fun run(locale: Locale): IntentResult {
        val now = Instant.now(clock)
        val upcoming = calendar.upcomingMeetings(within = 30.minutes)
            .firstOrNull { it.startsAt > now }
        if (upcoming == null) {
            val msg = if (locale.language == "fr") "Aucune rencontre dans les 30 prochaines minutes."
            else "No meeting in the next 30 minutes."
            tts.speak(msg, locale, interrupt = true)
            return IntentResult.Ok(spokenText = msg)
        }
        val req = BriefingRequest(BriefingType.PRE_MEETING, upcoming.id.toString(), now, locale)
        val result = generator.generate(req)
        tts.speak(result.text, locale, interrupt = true)
        return IntentResult.Ok(spokenText = result.text)
    }

    override suspend fun handle(intent: Intent.NextBrief): IntentResult =
        run(Locale.getDefault())
}
