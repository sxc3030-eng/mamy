package com.mamy.android.data.sms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.UUID

/**
 * Status of a sent SMS as understood by the UI layer.
 *
 * Mirrors the values defined by P9 spec
 * (`pending`/`sent`/`delivered`/`failed`/`cancelled`) but lives here so the UI
 * does not depend on the actual `SentSmsEntry` Room entity (which is being
 * shipped in parallel by W1-E).
 */
enum class SmsStatus(val raw: String) {
    PENDING("pending"),
    SENT("sent"),
    DELIVERED("delivered"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromRaw(raw: String?): SmsStatus = when (raw?.lowercase()) {
            "sent" -> SENT
            "delivered" -> DELIVERED
            "failed" -> FAILED
            "cancelled", "canceled" -> CANCELLED
            else -> PENDING
        }

        /** True when the user can retry sending this entry from the UI. */
        fun retryable(s: SmsStatus): Boolean = s == PENDING || s == FAILED
    }
}

/**
 * UI-layer row representing a sent SMS for a given person. Decoupled from the
 * Room `SentSmsEntry` so [PersonDetailViewModel] compiles without the W1-E
 * branch being merged.
 */
data class SentSmsRow(
    val id: UUID,
    val recipientPersonId: UUID?,
    val recipientPhone: String,
    val recipientDisplayName: String,
    val body: String,
    val sentAt: Instant,
    val status: SmsStatus,
    val failReason: String?,
    val segments: Int,
)

/**
 * Bridge interface between the UI layer and the (yet-to-be-merged) SentSmsDao.
 *
 * - W1-B (this branch) ships [EmptySentSmsRepository] as the default impl.
 * - W1-E (parallel) will land a real impl backed by `SentSmsDao` and bind it
 *   in [SmsModule] when both branches merge.
 *
 * Keeping the contract narrow (one observe + one retry) lets the swap happen
 * without touching the ViewModel or the screen.
 */
interface SentSmsRepository {
    fun observeForPerson(personId: UUID): Flow<List<SentSmsRow>>

    /**
     * Trigger a retry for a given SMS entry. May suspend on the network /
     * SmsManager call. Returns `true` if the retry was accepted (entry will
     * transition to `pending` -> `sent` async), `false` if it could not be
     * retried (e.g. permission missing, status not retryable).
     */
    suspend fun retry(entryId: UUID): Boolean
}

/**
 * Default no-op impl injected when the real `SentSmsDao` (W1-E) is not yet
 * present. Keeps the SMS tab visible and the ViewModel testable; the tab
 * simply renders the empty state.
 */
class EmptySentSmsRepository : SentSmsRepository {
    override fun observeForPerson(personId: UUID): Flow<List<SentSmsRow>> =
        flowOf(emptyList())

    override suspend fun retry(entryId: UUID): Boolean = false
}
