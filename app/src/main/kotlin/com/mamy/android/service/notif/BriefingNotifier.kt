package com.mamy.android.service.notif

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mamy.android.R
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.ui.play.PlayBriefingActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class BriefingNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init { BriefingNotifChannels.ensure(context) }

    open fun postDailyReady(locale: Locale) {
        val title = context.localized(R.string.notif_daily_title, locale)
        val body  = context.localized(R.string.notif_daily_body, locale)
        post(NOTIF_ID_DAILY, BriefingNotifChannels.CHANNEL_DAILY, title, body, deepLink("daily", null))
    }

    open fun postPreMeetingReady(meeting: MeetingEntity, locale: Locale) {
        val title = context.localized(R.string.notif_pre_title, locale)
        val body = context.localized(R.string.notif_pre_body, locale)
            .replace("{title}", meeting.title)
        post(notifIdFor(meeting), BriefingNotifChannels.CHANNEL_PRE_MEETING, title, body,
            deepLink("pre_meeting", meeting.id.toString()))
    }

    private fun deepLink(type: String, targetId: String?): PendingIntent {
        val intent = Intent(context, PlayBriefingActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("mamy://play/$type" + (targetId?.let { "?targetId=$it" } ?: ""))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, type.hashCode() + (targetId?.hashCode() ?: 0),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(id: Int, channelId: String, title: String, body: String, contentIntent: PendingIntent) {
        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_mamy_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(id, n)
    }

    private fun notifIdFor(meeting: MeetingEntity): Int = meeting.id.hashCode()

    companion object { const val NOTIF_ID_DAILY = 8001 }
}

private fun Context.localized(resId: Int, locale: Locale): String {
    val cfg = android.content.res.Configuration(resources.configuration)
    cfg.setLocale(locale)
    return createConfigurationContext(cfg).getString(resId)
}
