package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "meeting",
    indices = [
        Index(value = ["calendar_event_id"], unique = true),
        Index("starts_at"),
        Index("post_note_id"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["post_note_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MeetingEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "calendar_event_id") val calendarEventId: String?,
    val title: String,
    @ColumnInfo(name = "starts_at") val startsAt: Instant,
    @ColumnInfo(name = "ends_at") val endsAt: Instant,
    @ColumnInfo(name = "briefing_text") val briefingText: String?,
    @ColumnInfo(name = "post_note_id") val postNoteId: UUID?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
