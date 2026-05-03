package com.mamy.android.ui.settings

import app.cash.turbine.test
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.cost.MonthlyCost
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CostViewModelTest {

    @BeforeEach
    fun setupMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @Test
    fun `formats monthly costs into display rows`() = runTest {
        val tracker = mockk<LlmCostTracker> {
            every { monthlyCosts(any()) } returns flowOf(
                listOf(
                    MonthlyCost(provider = "claude", tokensIn = 1000, tokensOut = 500, microCents = 3500),
                    MonthlyCost(provider = "openai", tokensIn = 2000, tokensOut = 800, microCents = 780),
                )
            )
        }
        val vm = CostViewModel(tracker, LlmCostCalculator())

        vm.rows.test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("Anthropic Claude", items[0].displayName)
            assertEquals("$0.0035", items[0].costDisplay)
            assertEquals("$0.0008", items[1].costDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
