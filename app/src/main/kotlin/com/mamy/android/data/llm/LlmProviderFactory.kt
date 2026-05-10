package com.mamy.android.data.llm

import com.mamy.android.data.llm.claude.ClaudeProvider
import com.mamy.android.data.llm.gemini.GeminiProvider
import com.mamy.android.data.llm.ollama.OllamaProvider
import com.mamy.android.data.llm.openai.OpenAIProvider
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderFactory @Inject constructor(
    private val claude: Lazy<ClaudeProvider>,
    private val openai: Lazy<OpenAIProvider>,
    private val gemini: Lazy<GeminiProvider>,
    private val ollama: Lazy<OllamaProvider>,
) {
    fun byId(id: String): LlmProvider = when (id) {
        LlmProviderId.CLAUDE -> claude.get()
        LlmProviderId.OPENAI -> openai.get()
        LlmProviderId.GEMINI -> gemini.get()
        LlmProviderId.OLLAMA -> ollama.get()
        else -> throw IllegalArgumentException("Unknown LLM provider id: $id")
    }

    fun all(): List<LlmProvider> = listOf(claude.get(), openai.get(), gemini.get(), ollama.get())
}
