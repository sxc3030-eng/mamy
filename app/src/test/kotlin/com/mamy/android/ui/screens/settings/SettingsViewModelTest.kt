package com.mamy.android.ui.screens.settings

import app.cash.turbine.test
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.data.settings.SettingsRepository.Language
import com.mamy.android.data.settings.SettingsRepository.LlmProvider
import com.mamy.android.data.settings.SettingsRepository.PrivacyMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    private val repo = mockk<SettingsRepository>(relaxed = true)
    private val calendarSettings = mockk<CalendarSettings>(relaxed = true)
    private val costTracker = mockk<LlmCostTracker>(relaxed = true)

    private val languageFlow = MutableStateFlow(Language.SYSTEM)
    private val briefingHourFlow = MutableStateFlow(8)
    private val providerFlow = MutableStateFlow(LlmProvider.CLAUDE)
    private val privacyFlow = MutableStateFlow(PrivacyMode.STANDARD)
    private val wakeWordFlow = MutableStateFlow(1)
    private val calendarEnabledFlow = MutableStateFlow(false)

    private val smsMasterFlow = MutableStateFlow(false)
    private val smsConfirmFlow = MutableStateFlow(true)
    private val smsPrivacyFlow = MutableStateFlow(PrivacyMode.STANDARD)
    private val smsAutoRetryFlow = MutableStateFlow(false)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.languageFlow } returns languageFlow
        every { repo.dailyBriefingHourFlow } returns briefingHourFlow
        every { repo.selectedLlmProviderFlow } returns providerFlow
        every { repo.privacyModeFlow } returns privacyFlow
        every { repo.wakeWordSensitivityFlow } returns wakeWordFlow
        every { repo.smsMasterEnabledFlow } returns smsMasterFlow
        every { repo.smsConfirmRequiredFlow } returns smsConfirmFlow
        every { repo.smsPrivacyModeFlow } returns smsPrivacyFlow
        every { repo.smsAutoRetryEnabledFlow } returns smsAutoRetryFlow
        every { calendarSettings.isCalendarEnabled } returns calendarEnabledFlow
        every { costTracker.monthlyCosts(any()) } returns flowOf(emptyList())
    }

    @Test
    fun `initial state surfaces defaults`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.state.test {
            // Drain initial values; final emission has actual data.
            var s = awaitItem()
            // Allow up to 2 intermediate emissions while inner flows compose.
            repeat(2) {
                if (s == SettingsUiState()) s = awaitItem()
            }
            assertEquals(Language.SYSTEM, s.language)
            assertEquals(LlmProvider.CLAUDE, s.provider)
            assertEquals(PrivacyMode.STANDARD, s.privacyMode)
            assertEquals(false, s.sms.masterEnabled)
            assertTrue(s.sms.confirmRequired, "SMS confirm-required defaults ON per D19")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSmsMasterEnabled delegates to repo`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setSmsMasterEnabled(true)
        coVerify { repo.setSmsMasterEnabled(true) }
    }

    @Test
    fun `setSmsConfirmRequired delegates to repo`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setSmsConfirmRequired(false)
        coVerify { repo.setSmsConfirmRequired(false) }
    }

    @Test
    fun `setSmsPrivacyMode delegates to repo`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setSmsPrivacyMode(PrivacyMode.STRICT)
        coVerify { repo.setSmsPrivacyMode(PrivacyMode.STRICT) }
    }

    @Test
    fun `setLanguage delegates to repo`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setLanguage(Language.FR)
        coVerify { repo.setLanguage(Language.FR) }
    }

    @Test
    fun `setPrivacyMode delegates to repo`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setPrivacyMode(PrivacyMode.STRICT)
        coVerify { repo.setPrivacyMode(PrivacyMode.STRICT) }
    }

    @Test
    fun `setWakeWordSensitivity delegates to repo`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setWakeWordSensitivity(2)
        coVerify { repo.setWakeWordSensitivity(2) }
    }

    @Test
    fun `setCalendarEnabled delegates to settings`() = runTest {
        val vm = SettingsViewModel(repo, calendarSettings, costTracker)
        vm.setCalendarEnabled(true)
        coVerify { calendarSettings.setCalendarEnabled(true) }
    }
}
