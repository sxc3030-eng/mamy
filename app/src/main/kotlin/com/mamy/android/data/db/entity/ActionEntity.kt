package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "action",
    indices = [Index("linked_person_id"), Index("from_note_id"), Index("status"), Index("deadline")],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActionEntity(
    @PrimaryKey val id: UUID,
    val description: String,
    val assignee: String,
    @ColumnInfo(name = "linked_person_id") val linkedPersonId: UUID?,
    val deadline: Instant?,
    val status: String,
    @ColumnInfo(name = "from_note_id") val fromNoteId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "done_at") val doneAt: Instant?,
)
