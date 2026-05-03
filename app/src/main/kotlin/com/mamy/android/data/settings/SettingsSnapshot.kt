package com.mamy.android.data.settings

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Combined snapshot of all P6-relevant settings, used by briefing workers and TtsService.
 *
 *  - [dailyBriefingHour] / [dailyBriefingMinute] : when the daily briefing should fire
 *  - [locale] : null = follow system; otherwise overrides for both LLM prompt + TTS voice
 *  - [ttsRate] : 0.5 to 2.0
 *  - [zoneId] : used by [withinDailyWindow]
 */
data class SettingsSnapshot(
    val dailyBriefingHour: Int,
    val dailyBriefingMinute: Int,
    val locale: Locale?,
    val ttsRate: Float,
    val zoneId: ZoneId,
) {
    /**
     * True if [now] is within ±1 hour of the configured daily-briefing time.
     * Used by [com.mamy.android.service.work.DailyBriefingWorker] to self-gate
     * an hourly periodic worker — fires only inside the window.
     */
    fun withinDailyWindow(now: Instant): Boolean {
        val local = now.atZone(zoneId)
        val target = local.withHour(dailyBriefingHour).withMinute(dailyBriefingMinute).withSecond(0).withNano(0)
        val diffSec = kotlin.math.abs(Duration.between(target, local).seconds)
        return diffSec <= 3600 // ±1 hour
    }
}
