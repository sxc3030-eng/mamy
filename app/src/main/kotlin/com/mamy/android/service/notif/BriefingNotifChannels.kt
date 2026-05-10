package com.mamy.android.service.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.mamy.android.R

object BriefingNotifChannels {
    const val CHANNEL_DAILY = "mamy.briefing.daily"
    const val CHANNEL_PRE_MEETING = "mamy.briefing.pre_meeting"
    const val CHANNEL_MEETING_REMINDER = "mamy.meeting.reminder"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val daily = NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.notif_channel_daily),
            NotificationManager.IMPORTANCE_LOW, // silent
        )
        val pre = NotificationChannel(
            CHANNEL_PRE_MEETING,
            context.getString(R.string.notif_channel_pre_meeting),
            NotificationManager.IMPORTANCE_LOW, // silent (vibrates by default)
        ).apply { enableVibration(true) }
        val reminder = NotificationChannel(
            CHANNEL_MEETING_REMINDER,
            context.getString(R.string.notif_channel_meeting_reminder),
            NotificationManager.IMPORTANCE_DEFAULT, // makes a sound — it's a reminder
        ).apply { enableVibration(true) }
        nm.createNotificationChannel(daily)
        nm.createNotificationChannel(pre)
        nm.createNotificationChannel(reminder)
    }
}
