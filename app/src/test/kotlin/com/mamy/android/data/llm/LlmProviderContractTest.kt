package com.mamy.android.data.llm

import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.util.Lang
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmProviderContractTest {

    private val fake = object : LlmProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override suspend fun structure(req: LlmRequest): Result<LlmResponse> {
            return Result.success(
                LlmResponse(
                    note = StructuredNote(),
                    rawText = "{}",
                    tokensIn = 100,
                    tokensOut = 20,
                )
            )
        }
        override suspend fun testKey(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `provider returns a parsed StructuredNote and token usage`() = runTest {
        val req = LlmRequest(
            transcript = "hello",
            language = Lang.EN,
            systemPrompt = "test",
        )

        val resp = fake.structure(req).getOrThrow()

        assertEquals(100, resp.tokensIn)
        assertEquals(20, resp.tokensOut)
        assertTrue(resp.note.persons.isEmpty())
    }
}
