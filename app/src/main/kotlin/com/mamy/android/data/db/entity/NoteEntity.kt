package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Note: `meeting_id` is stored as a plain UUID without an FK constraint.
 * Room enforces FKs at the database layer, but we deliberately skip that
 * here so this entity can be introduced in P1.13 *before* MeetingEntity
 * (P1.17) without breaking incremental task compilation. Application-level
 * integrity is enforced by [com.mamy.android.data.db.dao.NoteDao] callers.
 */
@Entity(
    tableName = "note",
    indices = [Index("person_id"), Index("meeting_id"), Index("created_at")],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "person_id") val personId: UUID?,
    @ColumnInfo(name = "meeting_id") val meetingId: UUID?,
    @ColumnInfo(name = "raw_text") val rawText: String,
    @ColumnInfo(name = "structured_json") val structuredJson: String?,
    @ColumnInfo(name = "non_structured") val nonStructured: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "audio_duration_sec") val audioDurationSec: Int,
    @ColumnInfo(name = "llm_provider") val llmProvider: String,
    @ColumnInfo(name = "llm_cost_cents") val llmCostCents: Int?,
)
