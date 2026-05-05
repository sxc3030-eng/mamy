package com.mamy.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.llm.cost.LlmCostTracker
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.data.settings.SettingsRepository.Language
import com.mamy.android.data.settings.SettingsRepository.LlmProvider
import com.mamy.android.data.settings.SettingsRepository.PrivacyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing the modular [SettingsScreen]. Reads slice-flows from
 * [SettingsRepository] / [CalendarSettings] / [LlmCostTracker], combines into a
 * single [SettingsUiState], and exposes per-section setter methods that the
 * screen wires to its widgets.
 *
 * SMS-related state is sourced from the new SMS keys added by W1-C wave1-ui-3
 * to [SettingsRepository] (sms_master_enabled, sms_confirm_required,
 * sms_privacy_mode, sms_auto_retry_enabled).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val calendarSettings: CalendarSettings,
    costTracker: LlmCostTracker,
) : ViewModel() {

    /**
     * 11 source flows combine into one [SettingsUiState]. The 5-typed-arg [combine]
     * overload is exhausted, so we split into 3 sub-combines that ride a final
     * [combine] together.
     */
    private val coreFlow = combine(
        repo.languageFlow,
        repo.dailyBriefingHourFlow,
        repo.selectedLlmProviderFlow,
        repo.privacyModeFlow,
        repo.wakeWordSensitivityFlow,
    ) { lang, hour, provider, priv, wakeword ->
        Quintuple(lang, hour, provider, priv, wakeword)
    }

    private val sideFlow = combine(
        calendarSettings.isCalendarEnabled,
        costTracker.monthlyCosts(),
    ) { calConnected, costs -> calConnected to costs }

    private val smsFlow = combine(
        repo.smsMasterEnabledFlow,
        repo.smsConfirmRequiredFlow,
        repo.smsPrivacyModeFlow,
        repo.smsAutoRetryEnabledFlow,
    ) { master, confirm, mode, retry ->
        SmsSettingsUiState(
            masterEnabled = master,
            confirmRequired = confirm,
            privacyMode = mode,
            autoRetryEnabled = retry,
        )
    }

    val state: StateFlow<SettingsUiState> = combine(
        coreFlow,
        sideFlow,
        smsFlow,
    ) { core, side, sms ->
        SettingsUiState(
            language = core.a,
            dailyBriefingHour = core.b,
            ttsRate = 1.0f,
            provider = core.c,
            privacyMode = core.d,
            wakeWordSensitivity = core.e,
            calendarConnected = side.first,
            monthlyCosts = side.second,
            sms = sms,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    // ---------- General ----------
    fun setLanguage(value: Language) = viewModelScope.launch { repo.setLanguage(value) }
    fun setDailyBriefingTime(hour: Int, minute: Int) = viewModelScope.launch {
        repo.setDailyBriefTime(hour, minute)
    }

    // ---------- BYOK ----------
    fun setProvider(value: LlmProvider) = viewModelScope.launch {
        repo.setSelectedLlmProvider(value)
    }

    // ---------- Briefings ----------
    fun setTtsRate(value: Float) = viewModelScope.launch { repo.setTtsRate(value) }

    // ---------- Privacy ----------
    fun setPrivacyMode(value: PrivacyMode) = viewModelScope.launch { repo.setPrivacyMode(value) }

    // ---------- Wake word ----------
    fun setWakeWordSensitivity(level: Int) = viewModelScope.launch {
        repo.setWakeWordSensitivity(level)
    }

    // ---------- Calendar ----------
    fun setCalendarEnabled(enabled: Boolean) = viewModelScope.launch {
        calendarSettings.setCalendarEnabled(enabled)
    }

    // ---------- SMS (P9) ----------
    fun setSmsMasterEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setSmsMasterEnabled(enabled)
    }
    fun setSmsConfirmRequired(required: Boolean) = viewModelScope.launch {
        repo.setSmsConfirmRequired(required)
    }
    fun setSmsPrivacyMode(value: PrivacyMode) = viewModelScope.launch {
        repo.setSmsPrivacyMode(value)
    }
    fun setSmsAutoRetryEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setSmsAutoRetryEnabled(enabled)
    }
}
