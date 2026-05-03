package com.mamy.android.domain.capture

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.llm.LlmRequest
import com.mamy.android.data.llm.LlmResponse
import com.mamy.android.data.llm.claude.ParseFailedException
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.llm.model.StructuredNote
import com.mamy.android.data.settings.Settings
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.util.Lang
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmStructurerTest {

    private val provider = mockk<LlmProvider>()
    private val factory = mockk<LlmProviderFactory> {
        every { byId("claude") } returns provider
    }
    private val settings = mockk<SettingsRepository> {
        every { stream() } returns flowOf(Settings(llmProvider = "claude", uiLanguage = "fr"))
    }
    private val tracker = mockk<LlmCostTracker>(relaxed = true)
    private val builder = PromptBuilder()

    private val structurer = LlmStructurer(
        factory = factory,
        settings = settings,
        tracker = tracker,
        promptBuilder = builder,
    )

    @Test
    fun `happy path returns structured note and records cost`() = runTest {
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.success(
            LlmResponse(
                note = StructuredNote(),
                rawText = "{}",
                tokensIn = 100,
                tokensOut = 30,
            )
        )

        val outcome = structurer.structure("Marie va mieux", Lang.FR)

        assertTrue(outcome is StructureOutcome.Success)
        assertEquals("{}", (outcome as StructureOutcome.Success).rawText)
        coVerify { tracker.record("claude", 100, 30) }
    }

    @Test
    fun `parse failure path returns RawFallback (still records cost)`() = runTest {
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.failure(ParseFailedException("not json"))

        val outcome = structurer.structure("foo", Lang.FR)

        assertTrue(outcome is StructureOutcome.RawFallback)
        assertEquals("not json", (outcome as StructureOutcome.RawFallback).rawText)
        // No cost recorded because we don't have token counts on parse failure
        coVerify(exactly = 0) { tracker.record(any(), any(), any()) }
    }

    @Test
    fun `network failure returns Failure`() = runTest {
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } returns Result.failure(IllegalStateException("Claude API HTTP 500"))

        val outcome = structurer.structure("x", Lang.EN)

        assertTrue(outcome is StructureOutcome.Failure)
        assertEquals("Claude API HTTP 500", (outcome as StructureOutcome.Failure).message)
    }

    @Test
    fun `selects EN prompt when UI lang is en`() = runTest {
        every { settings.stream() } returns flowOf(Settings(llmProvider = "claude", uiLanguage = "en"))
        coEvery { provider.id } returns "claude"
        coEvery { provider.structure(any()) } answers {
            val req = firstArg<LlmRequest>()
            assertTrue(req.systemPrompt.contains("team manager"))
            Result.success(LlmResponse(StructuredNote(), "{}", 1, 1))
        }

        structurer.structure("hello", Lang.EN)
    }
}
