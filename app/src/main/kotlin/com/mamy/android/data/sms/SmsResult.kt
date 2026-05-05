package com.mamy.android.data.sms

import java.util.UUID

/**
 * P9 — Result of [SmsSender.send].
 *
 *  - [Sending] : SmsManager submit succeeded synchronously, broadcasts pending.
 *    `segments` reflects the result of `SmsManager.divideMessage(body)` — 1 for
 *    short messages, 2-3+ for multi-segment payloads.
 *  - [Sent] : convenience used after PendingIntent broadcast confirms RESULT_OK.
 *    The current V1 surface emits only `Sending` synchronously; `Sent` is used
 *    by the UI layer when re-reading the DAO row.
 *  - [Delivered] : delivery report received (V2 surface).
 *  - [Failed] : exception thrown by SmsManager. The DB row is updated to
 *    `failed` with the [reason] before this is returned.
 *  - [PermissionDenied] : `SEND_SMS` not granted at call time.
 *  - [NoCarrier] : SmsManager threw IllegalArgumentException for "no service"
 *    (rare, normally surfaced via PendingIntent).
 */
sealed class SmsResult {
    data class Sending(val entryId: UUID, val segments: Int) : SmsResult()
    data class Sent(val entryId: UUID) : SmsResult()
    data class Delivered(val entryId: UUID) : SmsResult()
    data class Failed(val reason: String) : SmsResult()
    data object PermissionDenied : SmsResult()
    data object NoCarrier : SmsResult()
}
