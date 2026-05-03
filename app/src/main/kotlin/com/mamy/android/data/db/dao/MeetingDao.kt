package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mamy.android.data.db.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface MeetingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meeting WHERE id = :id LIMIT 1")
    suspend fun getById(id: UUID): MeetingEntity?

    @Query("SELECT * FROM meeting WHERE calendar_event_id = :eventId LIMIT 1")
    suspend fun getByCalendarEventId(eventId: String): MeetingEntity?

    @Query("SELECT * FROM meeting WHERE calendar_event_id = :evtId LIMIT 1")
    suspend fun findByCalendarEventId(evtId: String): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meeting: MeetingEntity)

    @Query("DELETE FROM meeting WHERE calendar_event_id = :evtId")
    suspend fun deleteByCalendarEventId(evtId: String)

    @Query("SELECT * FROM meeting WHERE starts_at BETWEEN :from AND :to ORDER BY starts_at ASC")
    suspend fun getInRange(from: Instant, to: Instant): List<MeetingEntity>

    @Query("SELECT * FROM meeting WHERE starts_at BETWEEN :from AND :to ORDER BY starts_at ASC")
    fun observeInRange(from: Instant, to: Instant): Flow<List<MeetingEntity>>

    @Query("DELETE FROM meeting WHERE id = :id")
    suspend fun deleteById(id: UUID)

    // ----- P6 Briefing aliases (plan-spec names) -----

    @Query("SELECT * FROM meeting WHERE starts_at >= :from AND starts_at < :to ORDER BY starts_at ASC")
    suspend fun between(from: Instant, to: Instant): List<MeetingEntity>

    @Query("SELECT * FROM meeting WHERE id = :id LIMIT 1")
    suspend fun byId(id: UUID): MeetingEntity?

    @Query("SELECT person_id FROM meeting_attendee WHERE meeting_id = :meetingId")
    suspend fun attendeesOf(meetingId: UUID): List<UUID>
}
