package com.mamy.android.ui.screens.settings

import com.mamy.android.data.llm.cost.MonthlyCost
import com.mamy.android.data.settings.SettingsRepository.Language
import com.mamy.android.data.settings.SettingsRepository.LlmProvider
import com.mamy.android.data.settings.SettingsRepository.PrivacyMode

/**
 * Aggregate UI state for the modular [SettingsScreen]. Each section reads only
 * the slice of state it needs; the full record lives here so a single
 * [SettingsViewModel.state] StateFlow drives the whole screen.
 */
data class SettingsUiState(
    val language: Language = Language.SYSTEM,
    val dailyBriefingHour: Int = 8,
    val dailyBriefingMinute: Int = 0,
    val ttsRate: Float = 1.0f,
    val provider: LlmProvider = LlmProvider.CLAUDE,
    val byokKeyMasked: String? = null,
    val privacyMode: PrivacyMode = PrivacyMode.STANDARD,
    val wakeWordSensitivity: Int = 1,
    val calendarConnected: Boolean = false,
    val monthlyCosts: List<MonthlyCost> = emptyList(),
    // P9 SMS section state
    val sms: SmsSettingsUiState = SmsSettingsUiState(),
)

data class SmsSettingsUiState(
    val masterEnabled: Boolean = false,
    val confirmRequired: Boolean = true,
    val privacyMode: PrivacyMode = PrivacyMode.STANDARD,
    val autoRetryEnabled: Boolean = false,
    val sendSmsPermissionGranted: Boolean = false,
    val readContactsPermissionGranted: Boolean = false,
)
