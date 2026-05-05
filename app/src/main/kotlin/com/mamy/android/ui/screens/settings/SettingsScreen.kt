package com.mamy.android.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R
import com.mamy.android.data.settings.SettingsRepository.Language
import com.mamy.android.data.settings.SettingsRepository.LlmProvider
import com.mamy.android.data.settings.SettingsRepository.PrivacyMode

/**
 * Hilt entry-point used by the nav graph. Wires [SettingsViewModel] to
 * [SettingsScreen] and forwards navigation callbacks for NetworkLog, Data,
 * SMS audit log.
 */
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenNetworkLog: () -> Unit,
    onOpenData: () -> Unit = {},
    onOpenSmsAuditLog: () -> Unit = onOpenNetworkLog,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onLanguage = viewModel::setLanguage,
            onBriefingTime = { h, m -> viewModel.setDailyBriefingTime(h, m) },
            onProvider = viewModel::setProvider,
            onTtsRate = viewModel::setTtsRate,
            onPrivacyMode = viewModel::setPrivacyMode,
            onWakeWordSensitivity = viewModel::setWakeWordSensitivity,
            onCalendarEnabled = viewModel::setCalendarEnabled,
            onConnectCalendar = { viewModel.setCalendarEnabled(true) },
            onDisconnectCalendar = { viewModel.setCalendarEnabled(false) },
            onSmsMasterEnabled = viewModel::setSmsMasterEnabled,
            onSmsConfirmRequired = viewModel::setSmsConfirmRequired,
            onSmsPrivacyMode = viewModel::setSmsPrivacyMode,
            onSmsAutoRetryEnabled = viewModel::setSmsAutoRetryEnabled,
            onOpenSmsAuditLog = onOpenSmsAuditLog,
            onOpenNetworkLog = onOpenNetworkLog,
            onOpenData = onOpenData,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_settings_title)) }) },
        modifier = Modifier.testTag("settings-screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExpandableSection(
                title = stringResource(R.string.settings_section_general_title),
                tag = "settings-section-general",
                initiallyExpanded = true,
            ) {
                GeneralSection(state, actions)
            }
            ExpandableSection(
                title = stringResource(R.string.settings_section_byok_title),
                tag = "settings-section-byok",
            ) {
                ByokSection(state, actions)
            }
            ExpandableSection(
                title = stringResource(R.string.settings_section_briefings_title),
                tag = "settings-section-briefings",
            ) {
                BriefingsSection(state, actions)
            }
            ExpandableSection(
                title = stringResource(R.string.settings_section_privacy_title),
                tag = "settings-section-privacy",
            ) {
                PrivacySection(state, actions)
            }
            ExpandableSection(
                title = stringResource(R.string.settings_section_calendar_title),
                tag = "settings-section-calendar",
            ) {
                CalendarSection(state, actions)
            }
            // P9 NEW
            ExpandableSection(
                title = stringResource(R.string.settings_section_sms_title),
                tag = "settings-section-sms",
            ) {
                SmsSection(state, actions)
            }
            ExpandableSection(
                title = stringResource(R.string.settings_section_wakeword_title),
                tag = "settings-section-wakeword",
            ) {
                WakeWordSection(state, actions)
            }
            ExpandableSection(
                title = stringResource(R.string.settings_section_about_title),
                tag = "settings-section-about",
            ) {
                AboutSection(state, actions)
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    tag: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("$tag-header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "▾" else "▸")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}

@Composable
private fun GeneralSection(state: SettingsUiState, actions: SettingsActions) {
    Text(stringResource(R.string.settings_lang_label), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageButton(state, actions, Language.SYSTEM, R.string.settings_lang_system)
        LanguageButton(state, actions, Language.EN, R.string.settings_lang_en)
        LanguageButton(state, actions, Language.FR, R.string.settings_lang_fr)
    }
    Text(
        stringResource(R.string.settings_briefing_time_label),
        style = MaterialTheme.typography.labelLarge,
    )
    Text(
        stringResource(
            R.string.settings_briefing_time_value,
            state.dailyBriefingHour,
            state.dailyBriefingMinute,
        ),
        modifier = Modifier
            .clickable { actions.onBriefingTime(state.dailyBriefingHour, state.dailyBriefingMinute) }
            .testTag("settings-briefing-time"),
    )
}

@Composable
private fun LanguageButton(
    state: SettingsUiState,
    actions: SettingsActions,
    target: Language,
    labelRes: Int,
) {
    val selected = state.language == target
    OutlinedButton(
        onClick = { actions.onLanguage(target) },
        modifier = Modifier.testTag("settings-lang-${target.name.lowercase()}"),
    ) {
        Text(
            stringResource(labelRes),
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null,
        )
    }
}

@Composable
private fun ByokSection(state: SettingsUiState, actions: SettingsActions) {
    Text(stringResource(R.string.settings_byok_provider_label), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderButton(state, actions, LlmProvider.CLAUDE, R.string.settings_byok_claude)
        ProviderButton(state, actions, LlmProvider.OPENAI, R.string.settings_byok_openai)
        ProviderButton(state, actions, LlmProvider.GEMINI, R.string.settings_byok_gemini)
    }
    Text(
        stringResource(
            R.string.settings_byok_key_masked,
            state.byokKeyMasked ?: "—",
        ),
        modifier = Modifier.testTag("settings-byok-key-masked"),
    )
    OutlinedButton(
        onClick = actions.onTestByokConnection,
        modifier = Modifier.testTag("settings-byok-test"),
    ) { Text(stringResource(R.string.settings_byok_test)) }

    val totalCents = state.monthlyCosts.sumOf { it.microCents } / 10_000L
    Text(
        stringResource(R.string.settings_byok_cost_month, totalCents / 100.0),
        style = MaterialTheme.typography.titleMedium,
    )
    state.monthlyCosts.forEach { c ->
        Text(
            stringResource(
                R.string.settings_byok_cost_per_provider,
                c.provider,
                (c.microCents / 10_000L) / 100.0,
            ),
        )
    }
}

@Composable
private fun ProviderButton(
    state: SettingsUiState,
    actions: SettingsActions,
    target: LlmProvider,
    labelRes: Int,
) {
    val selected = state.provider == target
    OutlinedButton(
        onClick = { actions.onProvider(target) },
        modifier = Modifier.testTag("settings-byok-${target.name.lowercase()}"),
    ) {
        Text(
            stringResource(labelRes),
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null,
        )
    }
}

@Composable
private fun BriefingsSection(state: SettingsUiState, actions: SettingsActions) {
    Text(stringResource(R.string.settings_tts_rate_label), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.settings_tts_rate_value, state.ttsRate))
    Slider(
        value = state.ttsRate,
        onValueChange = actions.onTtsRate,
        valueRange = 0.5f..2.0f,
        steps = 5,
        modifier = Modifier.testTag("settings-tts-rate"),
    )
}

@Composable
private fun PrivacySection(state: SettingsUiState, actions: SettingsActions) {
    Text(stringResource(R.string.settings_privacy_mode_label), style = MaterialTheme.typography.labelLarge)
    PrivacyRadio(
        selected = state.privacyMode,
        target = PrivacyMode.STANDARD,
        labelRes = R.string.settings_privacy_mode_standard,
        onSelect = actions.onPrivacyMode,
        tag = "settings-privacy-standard",
    )
    PrivacyRadio(
        selected = state.privacyMode,
        target = PrivacyMode.STRICT,
        labelRes = R.string.settings_privacy_mode_strict,
        onSelect = actions.onPrivacyMode,
        tag = "settings-privacy-strict",
    )
    PrivacyRadio(
        selected = state.privacyMode,
        target = PrivacyMode.HYBRID_REDACTION,
        labelRes = R.string.settings_privacy_mode_hybrid,
        onSelect = actions.onPrivacyMode,
        enabled = false,
        tag = "settings-privacy-hybrid",
    )
}

@Composable
private fun CalendarSection(state: SettingsUiState, actions: SettingsActions) {
    Text(
        if (state.calendarConnected)
            stringResource(R.string.settings_calendar_status_connected, "—")
        else
            stringResource(R.string.settings_calendar_disabled_status),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = actions.onConnectCalendar,
            modifier = Modifier.testTag("settings-calendar-connect"),
        ) { Text(stringResource(R.string.settings_calendar_connect_google)) }
        OutlinedButton(
            onClick = actions.onDisconnectCalendar,
            modifier = Modifier.testTag("settings-calendar-disconnect"),
        ) { Text(stringResource(R.string.settings_calendar_disconnect)) }
    }
}

@Composable
private fun SmsSection(state: SettingsUiState, actions: SettingsActions) {
    ToggleRow(
        label = stringResource(R.string.settings_sms_master),
        checked = state.sms.masterEnabled,
        onChange = actions.onSmsMasterEnabled,
        tag = "settings-sms-master",
    )
    PermissionRow(
        label = stringResource(R.string.settings_sms_perm_send_status),
        granted = state.sms.sendSmsPermissionGranted,
        onRequest = actions.onRequestSendSmsPermission,
        tag = "settings-sms-perm-send",
    )
    PermissionRow(
        label = stringResource(R.string.settings_sms_perm_contacts_status),
        granted = state.sms.readContactsPermissionGranted,
        onRequest = actions.onRequestReadContactsPermission,
        tag = "settings-sms-perm-contacts",
    )
    ToggleRow(
        label = stringResource(R.string.settings_sms_confirm_required),
        checked = state.sms.confirmRequired,
        onChange = actions.onSmsConfirmRequired,
        tag = "settings-sms-confirm-required",
    )
    Text(stringResource(R.string.settings_sms_privacy_label), style = MaterialTheme.typography.labelLarge)
    PrivacyRadio(
        selected = state.sms.privacyMode,
        target = PrivacyMode.STANDARD,
        labelRes = R.string.settings_privacy_mode_standard,
        onSelect = actions.onSmsPrivacyMode,
        tag = "settings-sms-privacy-standard",
    )
    PrivacyRadio(
        selected = state.sms.privacyMode,
        target = PrivacyMode.STRICT,
        labelRes = R.string.settings_privacy_mode_strict,
        onSelect = actions.onSmsPrivacyMode,
        tag = "settings-sms-privacy-strict",
    )
    PrivacyRadio(
        selected = state.sms.privacyMode,
        target = PrivacyMode.HYBRID_REDACTION,
        labelRes = R.string.settings_privacy_mode_hybrid,
        onSelect = actions.onSmsPrivacyMode,
        enabled = false,
        tag = "settings-sms-privacy-hybrid",
    )
    DisabledToggleRow(
        label = stringResource(R.string.settings_sms_auto_retry),
        checked = state.sms.autoRetryEnabled,
        tag = "settings-sms-auto-retry",
    )
    OutlinedButton(
        onClick = actions.onOpenSmsAuditLog,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-sms-audit-log"),
    ) { Text(stringResource(R.string.settings_sms_audit_log)) }
}

@Composable
private fun WakeWordSection(state: SettingsUiState, actions: SettingsActions) {
    Text(stringResource(R.string.settings_wakeword_sensitivity_label), style = MaterialTheme.typography.labelLarge)
    val labels = listOf(
        R.string.settings_wakeword_sensitivity_low,
        R.string.settings_wakeword_sensitivity_med,
        R.string.settings_wakeword_sensitivity_high,
    )
    Text(stringResource(labels[state.wakeWordSensitivity.coerceIn(0, 2)]))
    Slider(
        value = state.wakeWordSensitivity.toFloat(),
        onValueChange = { actions.onWakeWordSensitivity(it.toInt().coerceIn(0, 2)) },
        valueRange = 0f..2f,
        steps = 1,
        modifier = Modifier.testTag("settings-wakeword-slider"),
    )
    OutlinedButton(
        onClick = actions.onTestWakeWord,
        modifier = Modifier.testTag("settings-wakeword-test"),
    ) { Text(stringResource(R.string.settings_wakeword_test)) }
}

@Composable
private fun AboutSection(state: SettingsUiState, actions: SettingsActions) {
    Text(
        stringResource(R.string.settings_about_version, com.mamy.android.BuildConfig.VERSION_NAME),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
    OutlinedButton(
        onClick = actions.onOpenNetworkLog,
        modifier = Modifier.testTag("settings-open-network-log"),
    ) { Text(stringResource(R.string.settings_open_network_log)) }
    OutlinedButton(
        onClick = actions.onOpenData,
        modifier = Modifier.testTag("settings-open-data"),
    ) { Text(stringResource(R.string.settings_export_all)) }
    @Suppress("UNUSED_EXPRESSION") state
}

// ---------- shared row composables ----------

@Composable
private fun PrivacyRadio(
    selected: PrivacyMode,
    target: PrivacyMode,
    labelRes: Int,
    onSelect: (PrivacyMode) -> Unit,
    enabled: Boolean = true,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect(target) }
            .testTag(tag),
    ) {
        RadioButton(
            selected = selected == target,
            onClick = if (enabled) ({ onSelect(target) }) else null,
            enabled = enabled,
        )
        Text(
            stringResource(labelRes),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DisabledToggleRow(label: String, checked: Boolean, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label)
            Text(
                stringResource(R.string.settings_sms_v2_hint),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = false)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onRequest: () -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted) { onRequest() }
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(
            stringResource(
                if (granted) R.string.settings_sms_perm_granted else R.string.settings_sms_perm_missing,
            ),
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}
