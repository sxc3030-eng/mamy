package com.mamy.android.ui.screens.onboarding

import androidx.lifecycle.viewModelScope
import com.mamy.android.ui.BaseViewModel
import com.mamy.android.ui.onboarding.contracts.BYOKManager
import com.mamy.android.ui.onboarding.contracts.OAuthResult
import com.mamy.android.ui.onboarding.contracts.OnboardingCalendarRepository
import com.mamy.android.ui.onboarding.contracts.OnboardingLlmProvider
import com.mamy.android.ui.onboarding.contracts.TestResult
import com.mamy.android.ui.onboarding.contracts.WakeWordTester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the multi-step onboarding flow (7 steps incl. SMS opt-in from P9).
 *
 * State transitions are gated:
 * - `next()` advances unconditionally (UI is responsible for not enabling Next
 *   until the current step is valid)
 * - `connectCalendar() / testByok() / testWakeWord()` advance ONLY on success;
 *   failures populate [OnboardingUiState.errorMessage]
 * - `back()` returns to the previous step
 *
 * Step 4 (Sms) is opt-in: user can either grant permissions and toggle on, or
 * skip via [skipSms] which sets [OnboardingUiState.smsOptIn] = false and
 * advances to Calendar.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val calendar: OnboardingCalendarRepository,
    private val byok: BYOKManager,
    private val wakeword: WakeWordTester,
) : BaseViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Linear advance with no guard — UI gates Next via [OnboardingUiState] flags. */
    fun next() = _state.update { it.copy(step = it.step.next(), errorMessage = null) }

    /** Linear back — first step stays put. */
    fun back() = _state.update { it.copy(step = it.step.previous(), errorMessage = null) }

    /** Step 1 — record permissions outcome from the screen. */
    fun setPermissionsGranted(granted: Boolean) =
        _state.update { it.copy(permissionsGranted = granted) }

    /** Step 2 — record Picovoice AccessKey + ppn files presence. */
    fun setWakeWordModel(accessKey: String, modelsPresent: Boolean) = _state.update {
        it.copy(
            picovoiceAccessKeyMasked = mask(accessKey),
            wakeWordModelsPresent = modelsPresent,
        )
    }

    /** Step 3 — validate BYOK key, advance on success. */
    fun testByok(provider: OnboardingLlmProvider, key: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        byok.testKey(provider, key).collectLatest { res ->
            _state.update { current ->
                when (res) {
                    TestResult.Ok -> current.copy(
                        step = OnboardingStep.Sms,
                        byokProvider = provider,
                        byokKeyMasked = mask(key),
                        isLoading = false,
                    )
                    is TestResult.Failed -> current.copy(
                        errorMessage = res.reason,
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Step 4 (NEW P9) — record SMS permissions + opt-in flag. */
    fun setSmsOptIn(optIn: Boolean, permissionsGranted: Boolean) =
        _state.update { it.copy(smsOptIn = optIn, smsPermissionsGranted = permissionsGranted) }

    /** Step 4 (NEW P9) — skip SMS step ("Plus tard"). */
    fun skipSms() = _state.update {
        it.copy(step = OnboardingStep.Calendar, smsOptIn = false, errorMessage = null)
    }

    /** Step 5 — connect Google Calendar; advance on success. */
    fun connectCalendar() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        calendar.connectGoogle().collectLatest { res ->
            _state.update { current ->
                when (res) {
                    is OAuthResult.Success -> current.copy(
                        step = OnboardingStep.WakeWord,
                        calendarAccount = res.email,
                        isLoading = false,
                    )
                    is OAuthResult.Failure -> current.copy(
                        errorMessage = res.message,
                        isLoading = false,
                    )
                    OAuthResult.Cancelled -> current.copy(isLoading = false)
                }
            }
        }
    }

    /** Step 5 — skip Calendar (it's optional). */
    fun skipCalendar() = _state.update {
        it.copy(step = OnboardingStep.WakeWord, errorMessage = null)
    }

    /** Step 6 — fire a test wake-word; advance on success. */
    fun testWakeWord() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        wakeword.testFire().collectLatest { fired ->
            _state.update {
                if (fired) {
                    it.copy(
                        step = OnboardingStep.Done,
                        wakeWordTested = true,
                        isLoading = false,
                    )
                } else {
                    it.copy(
                        errorMessage = "wake_word_not_detected",
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun mask(key: String): String =
        if (key.length <= 8) "****" else "${key.take(4)}…${key.takeLast(4)}"
}

/**
 * Linear next-step transition. [OnboardingStep.Done] is terminal.
 */
private fun OnboardingStep.next(): OnboardingStep = when (this) {
    OnboardingStep.Permissions -> OnboardingStep.WakeWordModel
    OnboardingStep.WakeWordModel -> OnboardingStep.Byok
    OnboardingStep.Byok -> OnboardingStep.Sms
    OnboardingStep.Sms -> OnboardingStep.Calendar
    OnboardingStep.Calendar -> OnboardingStep.WakeWord
    OnboardingStep.WakeWord -> OnboardingStep.Done
    OnboardingStep.Done -> OnboardingStep.Done
}

/**
 * Linear previous-step transition. [OnboardingStep.Permissions] is the floor.
 */
private fun OnboardingStep.previous(): OnboardingStep = when (this) {
    OnboardingStep.Permissions -> OnboardingStep.Permissions
    OnboardingStep.WakeWordModel -> OnboardingStep.Permissions
    OnboardingStep.Byok -> OnboardingStep.WakeWordModel
    OnboardingStep.Sms -> OnboardingStep.Byok
    OnboardingStep.Calendar -> OnboardingStep.Sms
    OnboardingStep.WakeWord -> OnboardingStep.Calendar
    OnboardingStep.Done -> OnboardingStep.WakeWord
}
