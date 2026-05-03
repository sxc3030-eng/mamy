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
}
