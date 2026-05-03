package com.mamy.android.data.llm.cost

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmCostDaoInstrumentedTest {

    private lateinit var db: LlmCostTestDatabase
    private lateinit var dao: LlmCostDao

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, LlmCostTestDatabase::class.java).build()
        dao = db.llmCostDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun aggregatesByProviderAndMonth() = runTest {
        val ym = YearMonth.of(2026, 5)
        val day1 = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val day15 = ym.atDay(15).atStartOfDay().toInstant(ZoneOffset.UTC)

        dao.insert(LlmCostEntry(provider = "claude", tokensIn = 1000, tokensOut = 500, costMicroCents = 4_000, createdAt = day1))
        dao.insert(LlmCostEntry(provider = "claude", tokensIn = 2000, tokensOut = 100, costMicroCents = 2_400, createdAt = day15))
        dao.insert(LlmCostEntry(provider = "openai", tokensIn = 500, tokensOut = 200, costMicroCents = 8_000, createdAt = day15))

        val rows = dao.aggregateForMonth(
            from = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            to = ym.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli(),
        ).first()

        assertEquals(2, rows.size)
        val claude = rows.first { it.provider == "claude" }
        assertEquals(3000, claude.tokensIn)
        assertEquals(600, claude.tokensOut)
        assertEquals(6_400L, claude.costMicroCents)
        val openai = rows.first { it.provider == "openai" }
        assertEquals(8_000L, openai.costMicroCents)
    }
}
