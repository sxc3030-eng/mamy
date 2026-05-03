package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "meeting_attendee",
    primaryKeys = ["meeting_id", "person_id"],
    indices = [Index("person_id")],
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meeting_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MeetingAttendeeEntity(
    @ColumnInfo(name = "meeting_id") val meetingId: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID,
)
