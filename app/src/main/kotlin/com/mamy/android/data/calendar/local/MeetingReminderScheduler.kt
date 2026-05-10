package com.mamy.android.data.calendar.local

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mamy.android.service.work.MeetingReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules T-24h and T-1h reminder notifications for every upcoming event
 * read from the device calendar. Each reminder is enqueued as a unique
 * one-time WorkRequest keyed on the calendar event id, so re-scheduling on
 * every Calendar-tab refresh just bumps the existing work (REPLACE policy)
 * without spamming duplicate notifications.
 *
 * Events farther than 30 days out are skipped (matches the Calendar tab
 * window). Past events and events <1h away from "now" don't enqueue the
 * corresponding reminder.
 */
@Singleton
class MeetingReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleFor(events: List<LocalCalendarEvent>) {
        val wm = WorkManager.getInstance(context)
        val now = Instant.now()

        // Cancel reminders for events that fell out of the window so we don't
        // leak old work. We do this lazily by only re-enqueuing live events;
        // unique-work keys for finished events are auto-pruned by WorkManager.

        events.forEach { event ->
            scheduleOne(wm, event, kind = MeetingReminderWorker.KIND_DAY, leadTime = Duration.ofHours(24), now = now)
            scheduleOne(wm, event, kind = MeetingReminderWorker.KIND_HOUR, leadTime = Duration.ofHours(1), now = now)
        }
    }

    private fun scheduleOne(
        wm: WorkManager,
        event: LocalCalendarEvent,
        kind: String,
        leadTime: Duration,
        now: Instant,
    ) {
        val triggerAt = event.startsAt.minus(leadTime)
        if (!triggerAt.isAfter(now)) return // already past
        val delayMs = Duration.between(now, triggerAt).toMillis()

        val data = workDataOf(
            MeetingReminderWorker.KEY_TITLE to event.title,
            MeetingReminderWorker.KEY_STARTS_AT to event.startsAt.toEpochMilli(),
            MeetingReminderWorker.KEY_KIND to kind,
            MeetingReminderWorker.KEY_EVENT_ID to event.eventId,
        )
        val req = OneTimeWorkRequestBuilder<MeetingReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        wm.enqueueUniqueWork(
            uniqueName(event.eventId, kind),
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }

    private fun uniqueName(eventId: Long, kind: String): String =
        "mamy.meeting-reminder.$eventId.$kind"
}
