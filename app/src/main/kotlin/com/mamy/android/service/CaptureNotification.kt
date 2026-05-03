package com.mamy.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.mamy.android.R
import com.mamy.android.domain.capture.CaptureEvent

object CaptureNotification {

    const val CHANNEL_ID = "mamy_listener"
    const val NOTIF_ID = 4242

    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.mamy_listener_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ctx.getString(R.string.mamy_listener_channel_desc)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    fun build(ctx: Context, event: CaptureEvent): Notification {
        val (iconRes, contentRes) = when (event) {
            is CaptureEvent.Idle, is CaptureEvent.Error,
            is CaptureEvent.NoSpeech -> R.drawable.ic_mamy_idle to R.string.notif_idle
            is CaptureEvent.WakeWordDetected,
            is CaptureEvent.Recording -> R.drawable.ic_mamy_capturing to R.string.notif_recording
            is CaptureEvent.Transcribing -> R.drawable.ic_mamy_capturing to R.string.notif_transcribing
            is CaptureEvent.TranscriptReady,
            is CaptureEvent.MaxDurationHit -> R.drawable.ic_mamy_idle to R.string.notif_idle
        }
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(ctx.getString(contentRes))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
