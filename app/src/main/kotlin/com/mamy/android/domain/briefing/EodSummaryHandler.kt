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
 * Handles `Intent.EodSummary` (« MamY, résume ma journée »).
 * Always real-time (no cache). Calls BriefingGenerator with EOD_SUMMARY type.
 *
 * Implements the existing `com.mamy.android.domain.intent.handler.EodSummaryHandler`
 * interface so it slots into the IntentDispatcher unchanged.
 */
@Singleton
class EodSummaryHandler @Inject constructor(
    private val generator: BriefingGenerator,
    private val tts: TtsService,
    private val clock: Clock = Clock.systemUTC(),
) : com.mamy.android.domain.intent.handler.EodSummaryHandler {

    suspend fun run(locale: Locale): IntentResult {
        val req = BriefingRequest(BriefingType.EOD_SUMMARY, null, Instant.now(clock), locale)
        val result = generator.generate(req)
        tts.speak(result.text, locale, interrupt = true)
        return IntentResult.Ok(spokenText = result.text)
    }

    override suspend fun handle(intent: Intent.EodSummary): IntentResult =
        run(Locale.getDefault())
}
