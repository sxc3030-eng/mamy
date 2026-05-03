package com.mamy.android.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "briefing",
    indices = [Index("type"), Index("target_id"), Index("expires_at")],
)
data class BriefingEntity(
    @PrimaryKey val id: UUID,
    val type: String,
    @ColumnInfo(name = "target_id") val targetId: String?,
    @ColumnInfo(name = "generated_at") val generatedAt: Instant,
    @ColumnInfo(name = "expires_at") val expiresAt: Instant,
    val text: String,
    @ColumnInfo(name = "llm_provider") val llmProvider: String,
    @ColumnInfo(name = "llm_cost_cents") val llmCostCents: Int?,
)
