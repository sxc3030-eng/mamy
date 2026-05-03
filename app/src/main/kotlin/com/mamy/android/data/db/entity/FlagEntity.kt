package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "flag",
    indices = [Index("person_id"), Index("type"), Index("resolved"), Index("from_note_id")],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FlagEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID,
    val type: String,
    val source: String,
    val severity: String,
    val note: String,
    val resolved: Boolean,
    @ColumnInfo(name = "from_note_id") val fromNoteId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
