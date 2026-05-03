package com.mamy.android.data.llm

import com.mamy.android.data.llm.claude.ClaudeProvider
import com.mamy.android.data.llm.gemini.GeminiProvider
import com.mamy.android.data.llm.openai.OpenAIProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderFactory @Inject constructor(
    private val claude: Lazy<ClaudeProvider>,
    private val openai: Lazy<OpenAIProvider>,
    private val gemini: Lazy<GeminiProvider>,
) {
    fun byId(id: String): LlmProvider = when (id) {
        LlmProviderId.CLAUDE -> claude.value
        LlmProviderId.OPENAI -> openai.value
        LlmProviderId.GEMINI -> gemini.value
        else -> throw IllegalArgumentException("Unknown LLM provider id: $id")
    }

    fun all(): List<LlmProvider> = listOf(claude.value, openai.value, gemini.value)
}
