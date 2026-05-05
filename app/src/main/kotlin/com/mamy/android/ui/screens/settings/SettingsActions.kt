package com.mamy.android.ui.screens.settings

import com.mamy.android.data.settings.SettingsRepository.Language
import com.mamy.android.data.settings.SettingsRepository.LlmProvider
import com.mamy.android.data.settings.SettingsRepository.PrivacyMode

/**
 * Bag of side-effect lambdas the SettingsScreen forwards to its ViewModel /
 * navigation. Kept as a single record so previews / instrumented tests can
 * supply a [NoOp] easily.
 */
data class SettingsActions(
    val onLanguage: (Language) -> Unit = {},
    val onBriefingTime: (Int, Int) -> Unit = { _, _ -> },
    val onProvider: (LlmProvider) -> Unit = {},
    val onTtsRate: (Float) -> Unit = {},
    val onPrivacyMode: (PrivacyMode) -> Unit = {},
    val onWakeWordSensitivity: (Int) -> Unit = {},
    val onCalendarEnabled: (Boolean) -> Unit = {},
    val onConnectCalendar: () -> Unit = {},
    val onDisconnectCalendar: () -> Unit = {},
    val onTestByokConnection: () -> Unit = {},
    val onTestWakeWord: () -> Unit = {},
    // SMS (P9)
    val onSmsMasterEnabled: (Boolean) -> Unit = {},
    val onSmsConfirmRequired: (Boolean) -> Unit = {},
    val onSmsPrivacyMode: (PrivacyMode) -> Unit = {},
    val onSmsAutoRetryEnabled: (Boolean) -> Unit = {},
    val onRequestSendSmsPermission: () -> Unit = {},
    val onRequestReadContactsPermission: () -> Unit = {},
    val onOpenSmsAuditLog: () -> Unit = {},
    // Navigation
    val onOpenNetworkLog: () -> Unit = {},
    val onOpenData: () -> Unit = {},
) {
    companion object {
        val NoOp = SettingsActions()
    }
}
