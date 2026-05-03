package com.mamy.android.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import java.util.UUID

@Dao
interface MeetingAttendeeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attendee: MeetingAttendeeEntity)

    @Query("SELECT person_id FROM meeting_attendee WHERE meeting_id = :meetingId")
    suspend fun getPersonIdsForMeeting(meetingId: UUID): List<UUID>

    @Query("SELECT meeting_id FROM meeting_attendee WHERE person_id = :personId")
    suspend fun getMeetingIdsForPerson(personId: UUID): List<UUID>

    @Query("DELETE FROM meeting_attendee WHERE meeting_id = :meetingId AND person_id = :personId")
    suspend fun delete(meetingId: UUID, personId: UUID)

    @Query("DELETE FROM meeting_attendee WHERE meeting_id = :meetingId")
    suspend fun deleteAllForMeeting(meetingId: UUID)
}
