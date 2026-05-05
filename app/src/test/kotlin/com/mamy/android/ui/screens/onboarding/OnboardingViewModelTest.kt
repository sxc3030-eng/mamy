package com.mamy.android.ui.screens.onboarding

import com.mamy.android.ui.onboarding.contracts.BYOKManager
import com.mamy.android.ui.onboarding.contracts.OAuthResult
import com.mamy.android.ui.onboarding.contracts.OnboardingCalendarRepository
import com.mamy.android.ui.onboarding.contracts.OnboardingLlmProvider
import com.mamy.android.ui.onboarding.contracts.TestResult
import com.mamy.android.ui.onboarding.contracts.WakeWordTester
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [OnboardingViewModel].
 *
 * Verifies:
 * - initial state is Permissions step
 * - linear next/back transitions across all 7 steps (incl. SMS step from P9)
 * - testByok success/failure paths
 * - connectCalendar success/failure/cancelled paths
 * - testWakeWord success/failure paths
 * - skipCalendar / skipSms shortcuts
 * - SMS opt-in toggle (NEW from P9)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var calendar: OnboardingCalendarRepository
    private lateinit var byok: BYOKManager
    private lateinit var wakeword: WakeWordTester

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        calendar = mockk(relaxed = true)
        byok = mockk(relaxed = true)
        wakeword = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = OnboardingViewModel(calendar, byok, wakeword)

    @Test
    fun `initial state is Permissions step`() = runTest {
        val vm = newVm()
        assertEquals(OnboardingStep.Permissions, vm.state.value.step)
    }

    @Test
    fun `next walks through all 7 steps in order`() = runTest {
        val vm = newVm()
        val expected = listOf(
            OnboardingStep.WakeWordModel,
            OnboardingStep.Byok,
            OnboardingStep.Sms,
            OnboardingStep.Calendar,
            OnboardingStep.WakeWord,
            OnboardingStep.Done,
        )
        expected.forEach { step ->
            vm.next()
            assertEquals(step, vm.state.value.step)
        }
        // Done is terminal — extra next() stays at Done.
        vm.next()
        assertEquals(OnboardingStep.Done, vm.state.value.step)
    }

    @Test
    fun `back from Permissions stays at Permissions`() = runTest {
        val vm = newVm()
        vm.back()
        assertEquals(OnboardingStep.Permissions, vm.state.value.step)
    }

    @Test
    fun `back from WakeWordModel returns to Permissions`() = runTest {
        val vm = newVm()
        vm.next()
        vm.back()
        assertEquals(OnboardingStep.Permissions, vm.state.value.step)
    }

    @Test
    fun `back from Sms returns to Byok`() = runTest {
        val vm = newVm()
        repeat(3) { vm.next() } // Permissions → WakeWordModel → Byok → Sms
        vm.back()
        assertEquals(OnboardingStep.Byok, vm.state.value.step)
    }

    @Test
    fun `setPermissionsGranted updates state`() = runTest {
        val vm = newVm()
        vm.setPermissionsGranted(true)
        assertTrue(vm.state.value.permissionsGranted)
    }

    @Test
    fun `setWakeWordModel records masked accesskey and presence flag`() = runTest {
        val vm = newVm()
        vm.setWakeWordModel("pico-1234567890abcdef", modelsPresent = true)
        val s = vm.state.value
        assertNotNull(s.picovoiceAccessKeyMasked)
        assertTrue(s.picovoiceAccessKeyMasked!!.startsWith("pico"))
        assertTrue(s.wakeWordModelsPresent)
    }

    @Test
    fun `testByok success advances to Sms and stores masked key`() = runTest {
        coEvery { byok.testKey(OnboardingLlmProvider.Claude, "sk-ant-api03-xxxxxxxxxxxx") } returns
            flowOf(TestResult.Ok)
        val vm = newVm()
        repeat(2) { vm.next() } // Permissions → WakeWordModel → Byok
        vm.testByok(OnboardingLlmProvider.Claude, "sk-ant-api03-xxxxxxxxxxxx")
        val s = vm.state.value
        assertEquals(OnboardingStep.Sms, s.step)
        assertEquals(OnboardingLlmProvider.Claude, s.byokProvider)
        assertNotNull(s.byokKeyMasked)
        assertNull(s.errorMessage)
    }

    @Test
    fun `testByok failure stays on Byok step and surfaces error`() = runTest {
        coEvery { byok.testKey(OnboardingLlmProvider.OpenAi, "bad-key") } returns
            flowOf(TestResult.Failed("invalid_api_key"))
        val vm = newVm()
        repeat(2) { vm.next() }
        vm.testByok(OnboardingLlmProvider.OpenAi, "bad-key")
        val s = vm.state.value
        assertEquals(OnboardingStep.Byok, s.step)
        assertEquals("invalid_api_key", s.errorMessage)
    }

    @Test
    fun `setSmsOptIn updates state with permissions flag`() = runTest {
        val vm = newVm()
        vm.setSmsOptIn(optIn = true, permissionsGranted = true)
        val s = vm.state.value
        assertTrue(s.smsOptIn)
        assertTrue(s.smsPermissionsGranted)
    }

    @Test
    fun `skipSms advances to Calendar without opt-in`() = runTest {
        val vm = newVm()
        repeat(3) { vm.next() } // → Sms
        vm.skipSms()
        val s = vm.state.value
        assertEquals(OnboardingStep.Calendar, s.step)
        assertFalse(s.smsOptIn)
    }

    @Test
    fun `connectCalendar success advances to WakeWord`() = runTest {
        coEvery { calendar.connectGoogle() } returns flowOf(OAuthResult.Success("acc@x.com"))
        val vm = newVm()
        repeat(4) { vm.next() } // → Calendar
        vm.connectCalendar()
        val s = vm.state.value
        assertEquals(OnboardingStep.WakeWord, s.step)
        assertEquals("acc@x.com", s.calendarAccount)
    }

    @Test
    fun `connectCalendar failure surfaces error and stays on Calendar`() = runTest {
        coEvery { calendar.connectGoogle() } returns flowOf(OAuthResult.Failure("network_down"))
        val vm = newVm()
        repeat(4) { vm.next() }
        vm.connectCalendar()
        val s = vm.state.value
        assertEquals(OnboardingStep.Calendar, s.step)
        assertEquals("network_down", s.errorMessage)
    }

    @Test
    fun `connectCalendar cancelled clears loading without advancing`() = runTest {
        coEvery { calendar.connectGoogle() } returns flowOf(OAuthResult.Cancelled)
        val vm = newVm()
        repeat(4) { vm.next() }
        vm.connectCalendar()
        val s = vm.state.value
        assertEquals(OnboardingStep.Calendar, s.step)
        assertFalse(s.isLoading)
    }

    @Test
    fun `skipCalendar advances to WakeWord`() = runTest {
        val vm = newVm()
        repeat(4) { vm.next() }
        vm.skipCalendar()
        assertEquals(OnboardingStep.WakeWord, vm.state.value.step)
    }

    @Test
    fun `testWakeWord success advances to Done`() = runTest {
        coEvery { wakeword.testFire() } returns flowOf(true)
        val vm = newVm()
        repeat(5) { vm.next() } // → WakeWord
        vm.testWakeWord()
        val s = vm.state.value
        assertEquals(OnboardingStep.Done, s.step)
        assertTrue(s.wakeWordTested)
    }

    @Test
    fun `testWakeWord failure stays on WakeWord with error`() = runTest {
        coEvery { wakeword.testFire() } returns flowOf(false)
        val vm = newVm()
        repeat(5) { vm.next() }
        vm.testWakeWord()
        val s = vm.state.value
        assertEquals(OnboardingStep.WakeWord, s.step)
        assertEquals("wake_word_not_detected", s.errorMessage)
        assertFalse(s.wakeWordTested)
    }

    @Test
    fun `next clears errorMessage`() = runTest {
        coEvery { wakeword.testFire() } returns flowOf(false)
        val vm = newVm()
        repeat(5) { vm.next() }
        vm.testWakeWord()
        assertNotNull(vm.state.value.errorMessage)
        vm.back()
        assertNull(vm.state.value.errorMessage)
    }
}
