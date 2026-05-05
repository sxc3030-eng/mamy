package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * P9 — Persisted record of an SMS dispatched (or attempted) by MamY.
 *
 * Inserted **before** the radio submit (status=`pending`) so a crash mid-send
 * still leaves an audit trail. The PendingIntent broadcast updates [status]
 * from `pending` → `sent` / `failed` / `delivered`.
 *
 * Statuses :
 *  - `pending`   : insert-time, not yet acknowledged by SmsManager
 *  - `sent`      : RESULT_OK from sentIntent
 *  - `delivered` : delivery report received (V2 surface only)
 *  - `failed`    : RESULT_ERROR_* from sentIntent OR exception thrown
 *  - `cancelled` : user said "non" or VoiceConfirmListener timed out
 */
@Entity(
    tableName = "sent_sms_entry",
    indices = [
        Index(value = ["recipient_person_id"]),
        Index(value = ["recipient_contact_id"]),
        Index(value = ["sent_at"]),
        Index(value = ["status"]),
    ],
)
data class SentSmsEntry(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "recipient_contact_id") val recipientContactId: String?,
    @ColumnInfo(name = "recipient_person_id") val recipientPersonId: UUID?,
    @ColumnInfo(name = "recipient_phone") val recipientPhone: String,
    @ColumnInfo(name = "recipient_display_name") val recipientDisplayName: String,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Instant,
    val status: String,
    @ColumnInfo(name = "fail_reason") val failReason: String?,
    @ColumnInfo(name = "raw_intent_text") val rawIntentText: String,
    val segments: Int = 1,
    @ColumnInfo(name = "privacy_mode") val privacyMode: String,
)
