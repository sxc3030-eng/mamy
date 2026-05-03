package com.mamy.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.cost.LlmCostCalculator
import com.mamy.android.data.llm.cost.LlmCostTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CostRow(
    val providerId: String,
    val displayName: String,
    val tokensIn: Int,
    val tokensOut: Int,
    val costDisplay: String,
)

@HiltViewModel
class CostViewModel @Inject constructor(
    tracker: LlmCostTracker,
    calculator: LlmCostCalculator,
) : ViewModel() {

    val rows: StateFlow<List<CostRow>> = tracker.monthlyCosts()
        .map { list ->
            list.map { mc ->
                CostRow(
                    providerId = mc.provider,
                    displayName = displayNameFor(mc.provider),
                    tokensIn = mc.tokensIn,
                    tokensOut = mc.tokensOut,
                    costDisplay = calculator.formatUsd(mc.microCents),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun displayNameFor(id: String) = when (id) {
        LlmProviderId.CLAUDE -> "Anthropic Claude"
        LlmProviderId.OPENAI -> "OpenAI GPT-4o"
        LlmProviderId.GEMINI -> "Google Gemini"
        else -> id
    }
}
