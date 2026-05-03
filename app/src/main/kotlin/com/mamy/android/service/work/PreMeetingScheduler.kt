package com.mamy.android.service.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamy.android.data.calendar.CalendarRepository
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.domain.briefing.BriefingGenerator
import com.mamy.android.domain.briefing.BriefingRequest
import com.mamy.android.domain.briefing.BriefingType
import com.mamy.android.service.notif.BriefingNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/**
 * Runs every minute (subject to OEM constraints — WorkManager floors to 15 min
 * minimum for periodic work, so for V1 we use a 15-min periodic worker that
 * loops internally with delay(60s) for ~14 cycles, OR we use [setForeground]
 * inside MamYListenerService for sub-15-min cadence. V1 chooses the
 * **listener-service piggyback** path : the foreground service already runs
 * for wake-word, so it dispatches a coroutine that calls [check] every 60 sec.
 *
 * This class exposes [check] as a pure suspending function so it can be unit-
 * tested without WorkManager. The HiltWorker form below exists for the
 * fallback path (if listener service isn't running).
 *
 * Behavior of [check] :
 *   - Find meetings starting in [4 min, 5 min) from now (1-minute slot).
 *   - For each such meeting, generate the PRE_MEETING briefing (cached for 1h
 *     so subsequent passes re-use it) and post a silent notification.
 *   - Window 4–5 min ensures we fire exactly once even if the worker is a tad
 *     early or late.
 */
class PreMeetingScheduler @javax.inject.Inject constructor(
    private val calendar: CalendarRepository,
    private val generator: BriefingGenerator,
    private val notifier: BriefingNotifier,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {

    suspend fun check() {
        val now = Instant.now(clock)
        val windowStart = now.plusSeconds(4 * 60)
        val windowEnd = now.plusSeconds(5 * 60)
        val cfg = settings.snapshot()
        val locale = cfg.locale ?: Locale.getDefault()
        val due = calendar.upcomingMeetings(within = 6.minutes)
            .filter { it.startsAt >= windowStart && it.startsAt < windowEnd }
        for (m in due) {
            val req = BriefingRequest(BriefingType.PRE_MEETING, m.id.toString(), now, locale)
            runCatching { generator.generate(req) }
                .onSuccess { notifier.postPreMeetingReady(m, locale) }
        }
    }
}

/**
 * Optional fallback: a HiltWorker that runs every 15 min (WorkManager minimum)
 * and loops internally for finer cadence. Use this only if MamYListenerService
 * isn't allowed (e.g., user disabled wake-word but still wants pre-meeting
 * briefings).
 */
@HiltWorker
class PreMeetingFallbackWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: PreMeetingScheduler,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        repeat(15) {
            scheduler.check()
            kotlinx.coroutines.delay(60_000)
        }
        return Result.success()
    }
    companion object { const val UNIQUE_NAME = "mamy-premeeting-fallback" }
}
