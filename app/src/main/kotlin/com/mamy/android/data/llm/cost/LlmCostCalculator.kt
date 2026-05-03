package com.mamy.android.data.llm.cost

import com.mamy.android.data.llm.LlmProviderId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-provider token pricing. Stored as microcents-per-million-tokens to avoid floats.
 *
 *   1 USD = 100 cents = 1_000_000 microcents (because 1 cent = 10_000 microcents)
 *   $1.00/M tokens   = 1_000_000 microcents per 1M tokens = 1 microcent per token
 */
@Singleton
class LlmCostCalculator @Inject constructor() {

    fun microCents(providerId: String, tokensIn: Int, tokensOut: Int): Long {
        val rates = when (providerId) {
            LlmProviderId.CLAUDE -> Rates(inMicroPerM = 1_000_000, outMicroPerM = 5_000_000) // $1.00 / $5.00
            LlmProviderId.OPENAI -> Rates(inMicroPerM = 150_000, outMicroPerM = 600_000)     // $0.15 / $0.60
            LlmProviderId.GEMINI -> Rates(inMicroPerM = 0, outMicroPerM = 0)
            else -> Rates(0, 0)
        }
        val inCost = (tokensIn.toLong() * rates.inMicroPerM) / 1_000_000L
        val outCost = (tokensOut.toLong() * rates.outMicroPerM) / 1_000_000L
        return inCost + outCost
    }

    /** Formats microcents to display dollars. Picks 2 or 4 decimals based on size. */
    fun formatUsd(microCents: Long): String {
        val cents = microCents / 10_000L                 // microcents → cents (truncate)
        val microRemainder = microCents % 10_000L
        val dollars = cents / 100
        val centRemainder = cents % 100
        return if (microCents in 1L..9_999L) {
            // sub-cent : show 4 decimals
            val total = microCents.toDouble() / 1_000_000.0
            String.format("$%.4f", total)
        } else {
            String.format("$%d.%02d", dollars, centRemainder)
        }
    }

    private data class Rates(val inMicroPerM: Long, val outMicroPerM: Long)
}
