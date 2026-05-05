package com.mamy.android.data.sms

import com.mamy.android.data.db.dao.SentSmsDao
import com.mamy.android.data.db.entity.SentSmsEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [SentSmsRepository] implementation backed by W1-E's [SentSmsDao].
 *
 * Replaces the [EmptySentSmsRepository] stub used by W1-B's PersonDetail SMS
 * tab during isolated branch development. Wired in [com.mamy.android.di.SmsUiModule]
 * via Hilt @Binds.
 */
@Singleton
class RealSentSmsRepository @Inject constructor(
    private val sentSmsDao: SentSmsDao,
) : SentSmsRepository {

    override fun observeForPerson(personId: java.util.UUID): Flow<List<SentSmsRow>> {
        return sentSmsDao.observeForPerson(personId).map { entries ->
            entries.map { it.toRow() }
        }
    }

    override suspend fun retry(entryId: java.util.UUID): Boolean {
        // V1 : just mark for retry; SmsRetryWorker (V1.1) will pick up.
        // For now flip status back to pending so the UI badge updates;
        // the actual SmsManager re-send is V1.1 scope.
        val current = sentSmsDao.findById(entryId) ?: return false
        if (!SmsStatus.retryable(SmsStatus.fromRaw(current.status))) return false
        sentSmsDao.updateStatus(entryId, "pending", null)
        return true
    }

    private fun SentSmsEntry.toRow(): SentSmsRow = SentSmsRow(
        id = id,
        recipientPersonId = recipientPersonId,
        recipientPhone = recipientPhone,
        recipientDisplayName = recipientDisplayName,
        body = body,
        sentAt = sentAt,
        status = SmsStatus.fromRaw(status),
        failReason = failReason,
        segments = segments,
    )
}
