package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "person",
    indices = [
        Index(value = ["email"]),
        Index(value = ["calendar_attendee_id"]),
        Index(value = ["last_interaction_at"]),
    ],
)
data class PersonEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val email: String?,
    @ColumnInfo(name = "role_hint") val roleHint: String?,
    @ColumnInfo(name = "calendar_attendee_id") val calendarAttendeeId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "last_interaction_at") val lastInteractionAt: Instant?,
    @ColumnInfo(name = "interaction_count") val interactionCount: Int,
    @ColumnInfo(name = "emotional_trend") val emotionalTrend: String?,
    val unmatched: Boolean,
    val archived: Boolean,
    @ColumnInfo(name = "android_contact_id") val androidContactId: String? = null,
)
