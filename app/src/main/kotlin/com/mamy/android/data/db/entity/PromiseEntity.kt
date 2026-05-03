package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "promise",
    indices = [Index("from_id"), Index("to_id"), Index("status"), Index("due"), Index("from_note_id")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PromiseEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "to_id") val toId: String,
    val what: String,
    val due: Instant?,
    val status: String,
    @ColumnInfo(name = "from_note_id") val fromNoteId: UUID,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Instant?,
)
