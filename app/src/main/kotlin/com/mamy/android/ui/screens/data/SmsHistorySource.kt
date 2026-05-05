package com.mamy.android.ui.screens.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI-facing summary of one sent SMS row, surfaced in [DataScreen] historique
 * and [com.mamy.android.ui.screens.sms.SmsHistoryScreen]. Decoupled from the
 * full [SentSmsEntry] Room entity that W1-E will land — UI just needs these
 * 6 fields. The merge phase wires [SmsHistorySource] to a real DAO-backed
 * implementation; until then the [NoOpSmsHistorySource] returns empty.
 */
data class SmsHistoryRow(
    val id: String,
    val contactName: String,
    val phoneNumber: String,
    val body: String,
    val status: SmsStatus,
    val sentAt: Instant,
)

enum class SmsStatus { PENDING, SENT, DELIVERED, FAILED, CANCELLED }

/**
 * Read-only feed of sent SMS for the UI layer. UI batch (W1-C wave1-ui-3) ships
 * the [NoOpSmsHistorySource] so the DataScreen renders without an SMS data layer
 * present. W1-E will replace the Hilt binding with the real DAO-backed
 * implementation in a follow-up commit.
 */
interface SmsHistorySource {
    /** Total count of SMS sent via MamY (status SENT or DELIVERED). */
    fun observeSentCount(): Flow<Int>

    /** All sent SMS rows newest-first. UI may filter by contact name / body / date. */
    fun observeAll(): Flow<List<SmsHistoryRow>>
}

/**
 * Default empty implementation. Hilt binding lives in [com.mamy.android.di.UiStubModule].
 * W1-E swaps this for a real DAO-backed SmsHistorySource in their merge.
 */
@Singleton
class NoOpSmsHistorySource @Inject constructor() : SmsHistorySource {
    private val zeroFlow: MutableStateFlow<Int> = MutableStateFlow(0)
    private val emptyFlow: MutableStateFlow<List<SmsHistoryRow>> = MutableStateFlow(emptyList())

    override fun observeSentCount(): Flow<Int> = zeroFlow.asStateFlow()
    override fun observeAll(): Flow<List<SmsHistoryRow>> = emptyFlow.asStateFlow()
}
