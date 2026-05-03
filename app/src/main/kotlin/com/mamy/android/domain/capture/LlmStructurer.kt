package com.mamy.android.domain.capture

import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.claude.ParseFailedException
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.util.Lang
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

sealed interface StructureOutcome {
    data class Success(
        val note: StructuredNote,
        val rawText: String,
        val providerId: String,
        val tokensIn: Int,
        val tokensOut: Int,
    ) : StructureOutcome

    /** Provider replied but JSON parse failed. Persist raw text with `non_structured=true`. */
    data class RawFallback(val rawText: String, val providerId: String) : StructureOutcome

    /** Network/auth/quota error. Capture should not be saved. */
    data class Failure(val message: String) : StructureOutcome
}

@Singleton
class LlmStructurer @Inject constructor(
    private val factory: LlmProviderFactory,
    private val settings: SettingsRepository,
    private val tracker: LlmCostTracker,
    private val promptBuilder: PromptBuilder,
) {

    suspend fun structure(transcript: String, language: Lang): StructureOutcome {
        val current = settings.stream().first()
        val provider = factory.byId(current.llmProvider)
        val req = LlmRequest(
            transcript = transcript,
            language = language,
            systemPrompt = promptBuilder.systemPrompt(language),
        )

        val result = provider.structure(req)

        return result.fold(
            onSuccess = { resp ->
                tracker.record(provider.id, resp.tokensIn, resp.tokensOut)
                StructureOutcome.Success(
                    note = resp.note,
                    rawText = resp.rawText,
                    providerId = provider.id,
                    tokensIn = resp.tokensIn,
                    tokensOut = resp.tokensOut,
                )
            },
            onFailure = { ex ->
                if (ex is ParseFailedException) {
                    StructureOutcome.RawFallback(rawText = ex.rawText, providerId = provider.id)
                } else {
                    StructureOutcome.Failure(message = ex.message ?: "Unknown error")
                }
            },
        )
    }
}
