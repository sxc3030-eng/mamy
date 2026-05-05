package com.mamy.android.data.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.mamy.android.data.db.dao.SentSmsDao
import com.mamy.android.data.db.entity.SentSmsEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P9 — Wraps [SmsManager] direct send (`SmsManager.sendTextMessage` /
 * `sendMultipartTextMessage`) with a permission gate, persistent audit row,
 * and PendingIntent plumbing for status callbacks.
 *
 * Flow per [send] call :
 *  1. Check `SEND_SMS` permission ; bail with [SmsResult.PermissionDenied] if absent.
 *  2. Insert [SentSmsEntry] with status `pending` BEFORE handing off to the radio
 *     (durable audit ; survives crashes during submit).
 *  3. Build sent + delivered PendingIntents tagged with the entry UUID. The
 *     [SmsStatusReceiver] consumes those broadcasts and updates the row to
 *     `sent` / `failed` / `delivered`.
 *  4. Run `divideMessage(body)` ; pick `sendTextMessage` for 1 part,
 *     `sendMultipartTextMessage` for 2+.
 *  5. Catch any thrown error, mark the row `failed`, return [SmsResult.Failed].
 *
 * The sender is intentionally side-effect heavy : it is the "edge" between
 * MamY's domain model and the Android telephony stack. All branching beyond
 * "pick a recipient" lives in [com.mamy.android.domain.intent.handler.TextToHandler].
 */
@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sentSmsDao: SentSmsDao,
    private val clock: Clock,
) {

    /**
     * Persist + dispatch an SMS.
     *
     * @param phoneE164 normalized recipient phone (the caller picks the number
     *   per the MOBILE > WORK > HOME > OTHER priority defined in the spec).
     * @param body SMS body, may exceed a single segment ; the caller decided
     *   length is acceptable.
     * @param recipientDisplayName label used for UI history rows.
     * @param contactId Android `ContactsContract` _ID, or null if no Android
     *   contact backed the lookup (e.g. Person table only).
     * @param linkedPersonId FK into the Person table when matched against
     *   the user's team.
     * @param rawIntentText original STT transcript, kept for audit.
     * @param privacyMode lowercase token persisted alongside the row
     *   (`standard`|`strict`|`hybrid_redaction`).
     */
    suspend fun send(
        phoneE164: String,
        body: String,
        recipientDisplayName: String,
        contactId: String?,
        linkedPersonId: UUID?,
        rawIntentText: String,
        privacyMode: String,
    ): SmsResult {
        if (!hasSendSmsPermission()) return SmsResult.PermissionDenied

        val sms: SmsManager = resolveSmsManager()
        val parts = sms.divideMessage(body) ?: arrayListOf(body)
        val segments = parts.size.coerceAtLeast(1)

        val entryId = UUID.randomUUID()
        val entry = SentSmsEntry(
            id = entryId,
            recipientContactId = contactId,
            recipientPersonId = linkedPersonId,
            recipientPhone = phoneE164,
            recipientDisplayName = recipientDisplayName,
            body = body,
            sentAt = clock.instant(),
            status = STATUS_PENDING,
            failReason = null,
            rawIntentText = rawIntentText,
            segments = segments,
            privacyMode = privacyMode,
        )
        sentSmsDao.insert(entry)

        val sentPI = makeSentPendingIntent(entryId)
        val deliveredPI = makeDeliveredPendingIntent(entryId)

        return runCatching {
            if (segments <= 1) {
                sms.sendTextMessage(phoneE164, null, body, sentPI, deliveredPI)
            } else {
                val sentList = ArrayList<PendingIntent>(segments).apply {
                    repeat(segments) { add(sentPI) }
                }
                val deliveredList = ArrayList<PendingIntent>(segments).apply {
                    repeat(segments) { add(deliveredPI) }
                }
                sms.sendMultipartTextMessage(
                    phoneE164,
                    null,
                    parts,
                    sentList,
                    deliveredList,
                )
            }
            SmsResult.Sending(entryId, segments)
        }.getOrElse { t ->
            sentSmsDao.updateStatus(entryId, STATUS_FAILED, t.message ?: "unknown")
            SmsResult.Failed(t.message ?: "unknown")
        }
    }

    private fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun resolveSmsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private fun makeSentPendingIntent(entryId: UUID): PendingIntent {
        val intent = Intent(SmsStatusReceiver.ACTION_SMS_SENT).apply {
            setPackage(context.packageName)
            putExtra(SmsStatusReceiver.EXTRA_ENTRY_ID, entryId.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            entryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun makeDeliveredPendingIntent(entryId: UUID): PendingIntent {
        val intent = Intent(SmsStatusReceiver.ACTION_SMS_DELIVERED).apply {
            setPackage(context.packageName)
            putExtra(SmsStatusReceiver.EXTRA_ENTRY_ID, entryId.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            entryId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }
}
