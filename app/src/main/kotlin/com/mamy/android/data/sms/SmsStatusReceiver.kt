package com.mamy.android.data.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.mamy.android.data.db.dao.SentSmsDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * P9 — Receives PendingIntent broadcasts emitted by [SmsSender] and updates
 * [com.mamy.android.data.db.entity.SentSmsEntry.status] accordingly.
 *
 * Wired dynamically from `MamYListenerService.onCreate()` (process-scoped
 * registration so the manifest stays clean — these intents are MamY-internal,
 * never sent by anyone else).
 *
 * Action map :
 *  - [ACTION_SMS_SENT] + RESULT_OK -> status `sent`, fail_reason null
 *  - [ACTION_SMS_SENT] + any other resultCode -> status `failed`, fail_reason
 *    is the lowercase SmsManager constant name (see [reasonFromCode]).
 *  - [ACTION_SMS_DELIVERED] -> status `delivered` (V2 surface only).
 */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject lateinit var sentSmsDao: SentSmsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(ctx: Context, intent: Intent) {
        val rawId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        val entryId = runCatching { UUID.fromString(rawId) }.getOrNull() ?: return

        when (intent.action) {
            ACTION_SMS_SENT -> handleSent(entryId, resultCode)
            ACTION_SMS_DELIVERED -> handleDelivered(entryId)
        }
    }

    private fun handleSent(entryId: UUID, code: Int) {
        if (code == Activity.RESULT_OK) {
            scope.launch { sentSmsDao.updateStatus(entryId, "sent", null) }
        } else {
            scope.launch { sentSmsDao.updateStatus(entryId, "failed", reasonFromCode(code)) }
        }
    }

    private fun handleDelivered(entryId: UUID) {
        scope.launch { sentSmsDao.updateStatus(entryId, "delivered", null) }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.mamy.android.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.mamy.android.SMS_DELIVERED"
        const val EXTRA_ENTRY_ID = "entry_id"

        /** Maps SmsManager.RESULT_ERROR_* constants to a lowercase audit token. */
        fun reasonFromCode(code: Int): String = when (code) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic_failure"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "no_service"
            SmsManager.RESULT_ERROR_NULL_PDU -> "null_pdu"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "radio_off"
            else -> "code_$code"
        }
    }
}
