package com.mamy.android.data.llm.gemini

import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.util.Lang
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiProviderTest {

    private val provider = GeminiProvider()

    @Test
    fun `structure returns NotImplementedV1 failure`() = runTest {
        val r = provider.structure(LlmRequest("x", Lang.EN, "s"))
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is GeminiNotImplementedException)
    }

    @Test
    fun `testKey returns failure`() = runTest {
        val r = provider.testKey()
        assertTrue(r.isFailure)
    }

    @Test
    fun `id and displayName are stable`() {
        assertEquals("gemini", provider.id)
        assertEquals("Google Gemini", provider.displayName)
    }
}
