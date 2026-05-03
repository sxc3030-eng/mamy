package com.mamy.android.data.settings

/**
 * Combined snapshot of user-controlled settings used by the LLM/structurer layer.
 * Strings (not enums) so the LLM layer can compare against
 * [com.mamy.android.data.llm.LlmProviderId] constants directly.
 */
data class Settings(
    val llmProvider: String,
    val uiLanguage: String,
)
