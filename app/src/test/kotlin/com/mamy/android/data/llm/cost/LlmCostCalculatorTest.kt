package com.mamy.android.data.llm.cost

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlmCostCalculatorTest {

    private val calc = LlmCostCalculator()

    @Test
    fun `claude haiku 1k in 500 out`() {
        // claude-3-5-haiku: $1.00/M input, $5.00/M output
        // 1000 in = $0.001 = 1000 microcents · 500 out = $0.0025 = 2500 microcents
        assertEquals(3500L, calc.microCents("claude", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `openai gpt-4o-mini`() {
        // gpt-4o-mini: $0.15/M input, $0.60/M output
        // 1000 in = $0.00015 = 150 microcents · 500 out = $0.0003 = 300 microcents
        assertEquals(450L, calc.microCents("openai", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `gemini returns 0 for stub`() {
        assertEquals(0L, calc.microCents("gemini", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `unknown provider returns 0`() {
        assertEquals(0L, calc.microCents("local", tokensIn = 1000, tokensOut = 500))
    }

    @Test
    fun `formats microcents to dollars`() {
        assertEquals("$0.0035", calc.formatUsd(3500))
        assertEquals("$1.23", calc.formatUsd(1_230_000))
        assertEquals("$0.00", calc.formatUsd(0))
    }
}
