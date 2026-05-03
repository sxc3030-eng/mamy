package com.mamy.android.domain.briefing

import com.mamy.android.data.tts.TtsService
import com.mamy.android.domain.intent.Intent
import com.mamy.android.domain.intent.IntentResult
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wired in P4's IntentRouter for `Intent.DailyBrief`.
 * Replaces the P4 stub that returned "non implémenté".
 *
 * NOTE: Implements both the plan-spec `run(locale)` method AND the existing
 * `com.mamy.android.domain.intent.handler.DailyBriefHandler` interface
 * (`handle(intent)`) so it slots into the IntentDispatcher unchanged.
 * The interface contract is preserved; `handle` delegates to `run`.
 */
@Singleton
class DailyBriefHandler @Inject constructor(
    private val generator: BriefingGenerator,
    private val tts: TtsService,
    private val clock: Clock = Clock.systemUTC(),
) : com.mamy.android.domain.intent.handler.DailyBriefHandler {

    suspend fun run(locale: Locale): IntentResult {
        val req = BriefingRequest(BriefingType.DAILY, null, Instant.now(clock), locale)
        val result = generator.generate(req)
        tts.speak(result.text, locale, interrupt = true)
        return IntentResult.Ok(spokenText = result.text)
    }

    override suspend fun handle(intent: Intent.DailyBrief): IntentResult =
        run(Locale.getDefault())
}
