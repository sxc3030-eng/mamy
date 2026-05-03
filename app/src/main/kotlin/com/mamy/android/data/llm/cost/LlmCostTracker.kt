package com.mamy.android.data.llm.cost

import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MonthlyCost(
    val provider: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val microCents: Long,
)

@Singleton
class LlmCostTracker @Inject constructor(
    private val dao: LlmCostDao,
    private val calculator: LlmCostCalculator,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    suspend fun record(provider: String, tokensIn: Int, tokensOut: Int) {
        val cost = calculator.microCents(provider, tokensIn, tokensOut)
        dao.insert(
            LlmCostEntry(
                provider = provider,
                tokensIn = tokensIn,
                tokensOut = tokensOut,
                costMicroCents = cost,
                createdAt = clock.instant(),
            )
        )
    }

    fun monthlyCosts(yearMonth: YearMonth = YearMonth.now(clock)): Flow<List<MonthlyCost>> {
        val zone = ZoneId.systemDefault()
        val from = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return dao.aggregateForMonth(from, to).map { rows ->
            rows.map {
                MonthlyCost(
                    provider = it.provider,
                    tokensIn = it.tokensIn,
                    tokensOut = it.tokensOut,
                    microCents = it.costMicroCents,
                )
            }
        }
    }
}
