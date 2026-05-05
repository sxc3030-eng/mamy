package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mamy.android.data.db.entity.SentSmsEntry
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * P9 SMS history DAO. Used by :
 *  - `SmsSender` to insert a `pending` row before radio submit + transition to `failed`
 *  - `SmsStatusReceiver` to update status from PendingIntent broadcasts
 *  - PersonDetailScreen to show the per-person SMS list (V1)
 *  - DataScreen to show the global SMS history (V1)
 */
@Dao
interface SentSmsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SentSmsEntry)

    @Query("UPDATE sent_sms_entry SET status = :status, fail_reason = :failReason WHERE id = :id")
    suspend fun updateStatus(id: UUID, status: String, failReason: String?)

    @Query("SELECT * FROM sent_sms_entry WHERE id = :id LIMIT 1")
    suspend fun findById(id: UUID): SentSmsEntry?

    @Query("SELECT * FROM sent_sms_entry WHERE recipient_person_id = :personId ORDER BY sent_at DESC")
    fun observeForPerson(personId: UUID): Flow<List<SentSmsEntry>>

    @Query("SELECT * FROM sent_sms_entry ORDER BY sent_at DESC")
    fun observeAll(): Flow<List<SentSmsEntry>>

    @Query("SELECT * FROM sent_sms_entry WHERE status = 'pending' ORDER BY sent_at ASC")
    fun getPendingFlow(): Flow<List<SentSmsEntry>>
}
