package com.mamy.android.data.llm.cost

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CostAggregate(
    val provider: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val costMicroCents: Long,
)

@Dao
interface LlmCostDao {

    @Insert
    suspend fun insert(entry: LlmCostEntry): Long

    @Query("""
        SELECT provider AS provider,
               SUM(tokensIn) AS tokensIn,
               SUM(tokensOut) AS tokensOut,
               SUM(costMicroCents) AS costMicroCents
        FROM llm_cost
        WHERE createdAt BETWEEN :from AND :to
        GROUP BY provider
        ORDER BY provider ASC
    """)
    fun aggregateForMonth(from: Long, to: Long): Flow<List<CostAggregate>>

    @Query("DELETE FROM llm_cost") suspend fun clear()
}
