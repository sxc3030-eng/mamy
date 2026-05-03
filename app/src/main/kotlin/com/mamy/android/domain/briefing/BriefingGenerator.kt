package com.mamy.android.domain.briefing

import com.mamy.android.data.llm.LlmProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for producing a briefing. Pipeline:
 *   1. Cache lookup (skip for non-cached types).
 *   2. Context assembly (Room queries).
 *   3. Prompt build.
 *   4. LLM call via the active provider.
 *   5. Cache persist.
 *   6. Return text.
 *
 * The class is provider-agnostic: it accepts [LlmProvider] (P3) so the user's
 * current BYOK pick (Claude/GPT/Gemini) is honored. Errors from the LLM
 * propagate; callers (handlers) decide whether to TTS-fallback to "désolé,
 * impossible de te briefer".
 */
@Singleton
class BriefingGenerator @Inject constructor(
    private val cache: BriefingCache,
    private val assembler: ContextAssembler,
    private val promptBuilder: BriefingPromptBuilder,
    private val llm: LlmProvider,
) {

    suspend fun generate(request: BriefingRequest): BriefingResult {
        cache.get(request.type, request.targetId)?.let { return it }
        val ctx = assembler.assemble(request)
        val prompt = promptBuilder.build(request.type, ctx, request.locale)
        val llmOut = llm.complete(
            systemPrompt = prompt.system,
            userPrompt = prompt.user,
            maxTokens = request.type.maxTokensFor(),
        )
        return cache.put(
            type = request.type,
            targetId = request.targetId,
            text = llmOut.text,
            providerName = llmOut.providerName,
            costCents = llmOut.costCents,
        )
    }

    private fun BriefingType.maxTokensFor(): Int = when (this) {
        BriefingType.DAILY        -> 280
        BriefingType.PRE_MEETING  -> 140
        BriefingType.PERSON_QUERY -> 200
        BriefingType.EOD_SUMMARY  -> 280
    }
}
