package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang

/**
 * Identifies a BYOK provider. Used as DB column value and DataStore key.
 */
object LlmProviderId {
    const val CLAUDE = "claude"
    const val OPENAI = "openai"
    const val GEMINI = "gemini"
    const val OLLAMA = "ollama"
}

data class LlmRequest(
    val transcript: String,
    val language: Lang,
    val systemPrompt: String,
)

data class LlmResponse(
    val note: StructuredNote,
    val rawText: String,
    val tokensIn: Int,
    val tokensOut: Int,
)

/**
 * Result of [LlmProvider.complete]. Used by P6 BriefingGenerator: the
 * generator only needs the spoken text + cost + provider name, not a
 * structured note.
 */
data class LlmResult(
    val text: String,
    val costCents: Int,
    val providerName: String,
)

/**
 * BYOK LLM provider abstraction. Each impl handles auth + request shape + response parsing.
 *
 * Implementations are stateless and resolve their API key on each call via [SecretsVault].
 */
interface LlmProvider {
    val id: String                 // matches LlmProviderId.*
    val displayName: String

    suspend fun structure(req: LlmRequest): Result<LlmResponse>

    /** 1-token call to verify the API key works. ~$0.0001. */
    suspend fun testKey(): Result<Unit>

    /**
     * Free-form completion used by P6 briefing generation. Default impl throws
     * `NotImplementedError` so providers without briefing support fail loudly.
     * Override in providers that support arbitrary prompts (Claude in V1).
     */
    suspend fun complete(systemPrompt: String, userPrompt: String, maxTokens: Int): LlmResult =
        throw NotImplementedError("Provider $id does not support free-form completion")
}
