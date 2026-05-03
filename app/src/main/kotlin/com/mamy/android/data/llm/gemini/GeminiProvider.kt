package com.mamy.android.data.llm.gemini

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderId
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import javax.inject.Inject
import javax.inject.Singleton

class GeminiNotImplementedException :
    UnsupportedOperationException("Gemini provider arrives in MamY V1.1.")

@Singleton
class GeminiProvider @Inject constructor() : LlmProvider {

    override val id = LlmProviderId.GEMINI
    override val displayName = "Google Gemini"

    override suspend fun structure(req: LlmRequest): Result<LlmResponse> =
        Result.failure(GeminiNotImplementedException())

    override suspend fun testKey(): Result<Unit> =
        Result.failure(GeminiNotImplementedException())
}
