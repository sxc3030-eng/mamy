package com.mamy.android.service.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mamy.android.MainActivity
import com.mamy.android.R
import com.mamy.android.service.notif.BriefingNotifChannels
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fires a notification reminding the user about an upcoming calendar event.
 *
 * Inputs (via [WorkerParameters.getInputData]):
 *   - "title": event title
 *   - "startsAtMillis": event start as epoch millis
 *   - "kind": "day" (T-24h) or "hour" (T-1h)
 *   - "eventId": Long, used as the notification id so re-scheduling overrides
 */
@HiltWorker
class MeetingReminderWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val startsAtMillis = inputData.getLong(KEY_STARTS_AT, 0)
        val kind = inputData.getString(KEY_KIND) ?: KIND_HOUR
        val eventId = inputData.getLong(KEY_EVENT_ID, 0)
        showNotification(title, startsAtMillis, kind, eventId)
        return Result.success()
    }

    private fun showNotification(title: String, startsAtMillis: Long, kind: String, eventId: Long) {
        BriefingNotifChannels.ensure(ctx)
        val nm = ContextCompat.getSystemService(ctx, android.app.NotificationManager::class.java)
            ?: return
        val tapIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            ctx,
            (eventId and 0x7fffffff).toInt(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFmt.format(Date(startsAtMillis))
        val (titleRes, bodyRes) = when (kind) {
            KIND_DAY -> R.string.reminder_day_title to R.string.reminder_day_body
            else -> R.string.reminder_hour_title to R.string.reminder_hour_body
        }
        val notif = NotificationCompat.Builder(ctx, BriefingNotifChannels.CHANNEL_MEETING_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(titleRes, title))
            .setContentText(ctx.getString(bodyRes, timeStr))
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Notification id: combine eventId + kind so day/hour don't clash
        val notifId = ((eventId and 0x7fffffff) * 2 + if (kind == KIND_DAY) 0 else 1).toInt()
        nm.notify(notifId, notif)
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_STARTS_AT = "startsAtMillis"
        const val KEY_KIND = "kind"
        const val KEY_EVENT_ID = "eventId"
        const val KIND_DAY = "day"
        const val KIND_HOUR = "hour"
    }
}
