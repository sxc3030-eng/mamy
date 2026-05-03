package com.mamy.android.data.llm.cost

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "llm_cost")
data class LlmCostEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,                 // matches LlmProviderId.*
    val tokensIn: Int,
    val tokensOut: Int,
    val costMicroCents: Long,             // 1 USD = 100_000_000 microcents (precision)
    val createdAt: Instant,
)
