package com.mamy.android.service.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

/**
 * Runs once per day at the configured time (default 8:00 local). Generates
 * the daily briefing, persists it in cache, and posts a silent notification :
 * « Briefing prêt — dis 'MamY ma journée' ».
 *
 * If the periodic-window check determines we're outside the configured window
 * (more than ±1h from settings.dailyBriefingTime), the worker is a no-op.
 * That way, the periodic worker can fire every hour and self-gate on time.
 */
@HiltWorker
class DailyBriefingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generator: BriefingGenerator,
    private val notifier: BriefingNotifier,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now(clock)
        val cfg = settings.snapshot()
        if (!cfg.withinDailyWindow(now)) return Result.success()

        val locale = cfg.locale ?: Locale.getDefault()
        val req = BriefingRequest(BriefingType.DAILY, null, now, locale)
        return runCatching { generator.generate(req) }
            .onSuccess { notifier.postDailyReady(locale) }
            .map { Result.success() }
            .getOrElse { Result.retry() }
    }

    companion object { const val UNIQUE_NAME = "mamy-daily-briefing" }
}
