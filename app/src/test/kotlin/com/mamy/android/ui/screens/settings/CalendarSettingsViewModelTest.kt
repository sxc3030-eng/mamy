package com.mamy.android.ui.screens.settings

import app.cash.turbine.test
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.domain.calendar.CalendarOnboardingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarSettingsViewModelTest {

    private val onboarding = mockk<CalendarOnboardingUseCase>(relaxed = true)
    private val auth = mockk<CalendarAuthManager>(relaxed = true)
    private val settings = mockk<CalendarSettings>(relaxed = true)
    private val enabledFlow = MutableStateFlow(false)

    @BeforeEach
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.isCalendarEnabled } returns enabledFlow
    }

    @Test
    fun `state reflects calendar_enabled flag`() = runTest {
        val vm = CalendarSettingsViewModel(onboarding, auth, settings)
        vm.uiState.test {
            assertEquals(false, awaitItem().calendarEnabled)
            enabledFlow.value = true
            assertEquals(true, awaitItem().calendarEnabled)
        }
    }

    @Test
    fun `onAuthCodeReceived calls onboarding`() = runTest {
        coEvery { onboarding.completeOnboarding(any(), any(), any()) } returns Result.success(Unit)
        val vm = CalendarSettingsViewModel(onboarding, auth, settings)
        vm.onAuthCodeReceived("c", "e@x.com", "s")
        coVerify { onboarding.completeOnboarding("c", "e@x.com", "s") }
    }

    @Test
    fun `onDisconnect calls onboarding disconnect`() = runTest {
        val vm = CalendarSettingsViewModel(onboarding, auth, settings)
        vm.onDisconnect()
        coVerify { onboarding.disconnect() }
    }
}
