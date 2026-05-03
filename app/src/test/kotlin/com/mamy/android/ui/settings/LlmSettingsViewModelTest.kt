package com.mamy.android.ui.settings

import app.cash.turbine.test
import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.data.settings.Settings
import com.mamy.android.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LlmSettingsViewModelTest {

    @BeforeEach
    fun setupMain() {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    private val claude = mockk<LlmProvider>().also {
        every { it.id } returns "claude"; every { it.displayName } returns "Anthropic Claude"
    }
    private val factory = mockk<LlmProviderFactory> {
        every { byId("claude") } returns claude
        every { all() } returns listOf(claude)
    }
    private val vault = mockk<SecretsVault>(relaxed = true)
    private val settingsFlow = MutableStateFlow(Settings(llmProvider = "claude", uiLanguage = "fr"))
    private val settings = mockk<SettingsRepository>(relaxed = true) {
        every { stream() } returns settingsFlow
    }

    private val vm = LlmSettingsViewModel(factory, vault, settings)

    @Test
    fun `available providers exposed`() = runTest {
        assertEquals(listOf("claude"), vm.availableProviders().map { it.id })
    }

    @Test
    fun `saveKey persists via vault`() = runTest {
        vm.saveKey("claude", "sk-ant-test-1234")
        coVerify { vault.setKey("claude", "sk-ant-test-1234") }
    }

    @Test
    fun `testKey returns success flow update`() = runTest {
        coEvery { claude.testKey() } returns Result.success(Unit)

        vm.testKey("claude")

        vm.lastTestResult.test {
            val emitted = awaitItem()
            assertTrue(emitted is KeyTestResult.Success)
            assertEquals("claude", (emitted as KeyTestResult.Success).providerId)
        }
    }

    @Test
    fun `testKey failure surfaces error`() = runTest {
        coEvery { claude.testKey() } returns Result.failure(IllegalStateException("HTTP 401"))

        vm.testKey("claude")

        vm.lastTestResult.test {
            val emitted = awaitItem()
            assertTrue(emitted is KeyTestResult.Failure)
            assertEquals("HTTP 401", (emitted as KeyTestResult.Failure).message)
        }
    }
}
