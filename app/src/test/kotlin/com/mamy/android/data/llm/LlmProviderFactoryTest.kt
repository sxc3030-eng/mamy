package com.mamy.android.data.llm

import com.mamy.android.data.llm.claude.ClaudeProvider
import com.mamy.android.data.llm.gemini.GeminiProvider
import com.mamy.android.data.llm.openai.OpenAIProvider
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LlmProviderFactoryTest {

    private val claude = mockk<ClaudeProvider>(relaxed = true) { /* id default */ }
    private val openai = mockk<OpenAIProvider>(relaxed = true)
    private val gemini = mockk<GeminiProvider>(relaxed = true)

    private val factory = LlmProviderFactory(
        claude = lazy { claude.also { io.mockk.every { it.id } returns "claude" } },
        openai = lazy { openai.also { io.mockk.every { it.id } returns "openai" } },
        gemini = lazy { gemini.also { io.mockk.every { it.id } returns "gemini" } },
    )

    @Test
    fun `selects claude provider`() {
        assertSame(claude, factory.byId("claude"))
    }

    @Test
    fun `selects openai provider`() {
        assertSame(openai, factory.byId("openai"))
    }

    @Test
    fun `selects gemini provider`() {
        assertSame(gemini, factory.byId("gemini"))
    }

    @Test
    fun `throws on unknown id`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { factory.byId("local") }
        assertEquals("Unknown LLM provider id: local", ex.message)
    }

    @Test
    fun `lists providers in stable order`() {
        val ids = factory.all().map { it.id }
        assertEquals(listOf("claude", "openai", "gemini"), ids)
    }
}
