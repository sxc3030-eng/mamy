package com.mamy.android.domain.briefing

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

class BriefingGeneratorTest {

    private val cache = mockk<BriefingCache>()
    private val assembler = mockk<ContextAssembler>()
    private val prompts = mockk<BriefingPromptBuilder>()
    private val llm = mockk<LlmProvider>()
    private val sut = BriefingGenerator(cache, assembler, prompts, llm)

    private val now = Instant.parse("2026-05-02T13:00:00Z")
    private val req = BriefingRequest(BriefingType.DAILY, null, now, Locale.FRENCH)

    @Test
    fun `cache hit short-circuits LLM call`() = runTest {
        val cached = BriefingResult("Salut Marc", now, now.plusSeconds(3600), cached = true, "claude", 0)
        coEvery { cache.get(BriefingType.DAILY, null) } returns cached

        val out = sut.generate(req)

        assertEquals("Salut Marc", out.text)
        assertTrue(out.cached)
        coVerify(exactly = 0) { llm.complete(any(), any(), any()) }
        coVerify(exactly = 0) { assembler.assemble(any()) }
    }

    @Test
    fun `cache miss runs full pipeline and persists result`() = runTest {
        coEvery { cache.get(BriefingType.DAILY, null) } returns null
        coEvery { assembler.assemble(req) } returns "{\"date\":\"2026-05-02\"}"
        coEvery { prompts.build(BriefingType.DAILY, any(), Locale.FRENCH) } returns
            BriefingPromptBuilder.Prompt("sys", "user")
        coEvery { llm.complete("sys", "user", 280) } returns LlmResult("Texte vocal", 7, "claude")
        val persisted = BriefingResult("Texte vocal", now, now.plusSeconds(8 * 3600), false, "claude", 7)
        coEvery { cache.put(BriefingType.DAILY, null, "Texte vocal", "claude", 7) } returns persisted

        val out = sut.generate(req)

        assertEquals("Texte vocal", out.text)
        assertEquals(false, out.cached)
        assertEquals(7, out.costCents)
        coVerify { llm.complete("sys", "user", 280) }
    }

    @Test
    fun `pre meeting uses 140 token budget`() = runTest {
        val r = req.copy(type = BriefingType.PRE_MEETING, targetId = "m1")
        coEvery { cache.get(BriefingType.PRE_MEETING, "m1") } returns null
        coEvery { assembler.assemble(r) } returns "{}"
        coEvery { prompts.build(BriefingType.PRE_MEETING, "{}", Locale.FRENCH) } returns
            BriefingPromptBuilder.Prompt("s", "u")
        coEvery { llm.complete("s", "u", 140) } returns LlmResult("ok", 2, "gpt")
        coEvery { cache.put(BriefingType.PRE_MEETING, "m1", "ok", "gpt", 2) } returns
            BriefingResult("ok", now, now.plusSeconds(3600), false, "gpt", 2)

        sut.generate(r)
        coVerify { llm.complete("s", "u", 140) }
    }

    @Test
    fun `LLM exception propagates`() = runTest {
        coEvery { cache.get(any(), any()) } returns null
        coEvery { assembler.assemble(any()) } returns "{}"
        coEvery { prompts.build(any(), any(), any()) } returns BriefingPromptBuilder.Prompt("s", "u")
        coEvery { llm.complete(any(), any(), any()) } throws IllegalStateException("api down")

        val ex = runCatching { sut.generate(req) }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
    }
}
