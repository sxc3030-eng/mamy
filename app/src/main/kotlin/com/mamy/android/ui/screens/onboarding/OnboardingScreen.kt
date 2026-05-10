package com.mamy.android.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mamy.android.R

/**
 * Hilt-wired entry point. Resolves [OnboardingViewModel] and forwards callbacks
 * to the stateless [OnboardingScreen] for previews + tests.
 */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinish: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onPermissionsGranted = { viewModel.setPermissionsGranted(it) },
        onWakeWordModelSet = { key, present -> viewModel.setWakeWordModel(key, present) },
        onSkipWakeWordModel = viewModel::skipWakeWordModel,
        onSmsOptIn = { optIn, perms -> viewModel.setSmsOptIn(optIn, perms) },
        onSkipSms = viewModel::skipSms,
        onConnectCalendar = viewModel::connectCalendar,
        onSkipCalendar = viewModel::skipCalendar,
        onTestWakeWord = viewModel::testWakeWord,
        onFinish = onFinish,
    )
}

/**
 * Stateless 6-step onboarding screen (V1.5 alpha — Byok step removed).
 *
 * Each step renders its own sub-Composable; advance/back is gated by the VM
 * state (see [OnboardingUiState]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPermissionsGranted: (Boolean) -> Unit,
    onWakeWordModelSet: (String, Boolean) -> Unit,
    onSkipWakeWordModel: () -> Unit,
    onSmsOptIn: (Boolean, Boolean) -> Unit,
    onSkipSms: () -> Unit,
    onConnectCalendar: () -> Unit,
    onSkipCalendar: () -> Unit,
    onTestWakeWord: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("onboarding-root"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            // Progress: 1/6…6/6
            val stepIndex = state.step.ordinal + 1
            val total = OnboardingStep.values().size
            Text(
                text = stringResource(R.string.onboarding_step_progress, stepIndex, total),
                style = MaterialTheme.typography.labelMedium,
            )
            LinearProgressIndicator(
                progress = { stepIndex.toFloat() / total.toFloat() },
                modifier = Modifier.testTag("onboarding-progress"),
            )

            when (state.step) {
                OnboardingStep.Permissions -> PermissionsStep(
                    granted = state.permissionsGranted,
                    onGranted = onPermissionsGranted,
                    onContinue = onNext,
                )
                OnboardingStep.WakeWordModel -> WakeWordModelStep(
                    accessKeyMasked = state.picovoiceAccessKeyMasked,
                    modelsPresent = state.wakeWordModelsPresent,
                    onAccessKeySet = onWakeWordModelSet,
                    onContinue = onNext,
                    onSkip = onSkipWakeWordModel,
                )
                OnboardingStep.Sms -> SmsStep(
                    optIn = state.smsOptIn,
                    permissionsGranted = state.smsPermissionsGranted,
                    onOptIn = onSmsOptIn,
                    onSkip = onSkipSms,
                    onContinue = onNext,
                )
                OnboardingStep.Calendar -> CalendarStep(
                    account = state.calendarAccount,
                    onConnect = onConnectCalendar,
                    onSkip = onSkipCalendar,
                )
                OnboardingStep.WakeWord -> WakeWordStep(
                    onTest = onTestWakeWord,
                    isLoading = state.isLoading,
                )
                OnboardingStep.Done -> DoneStep(onFinish = onFinish)
            }

            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("onboarding-error"),
                )
            }

            if (state.step != OnboardingStep.Permissions && state.step != OnboardingStep.Done) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("onboarding-btn-back"),
                ) {
                    Text(stringResource(R.string.onboarding_btn_back))
                }
            }
        }
    }
}

/* --------------------------------- Steps -------------------------------- */

@Composable
private fun PermissionsStep(
    granted: Boolean,
    onGranted: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("onboarding-step-permissions"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_permissions_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.onboarding_permissions_oem_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        // The actual accompanist multiPermissionState wiring lives in the screen
        // host (PermissionRequest helpers); for unit-test isolation we expose two
        // buttons : "Grant" (caller drives accompanist) and "Continue".
        Button(
            onClick = { onGranted(true) },
            modifier = Modifier.testTag("onboarding-btn-grant"),
        ) {
            Text(stringResource(R.string.onboarding_btn_grant))
        }
        Button(
            onClick = onContinue,
            enabled = granted,
            modifier = Modifier.testTag("onboarding-btn-continue"),
        ) {
            Text(stringResource(R.string.onboarding_btn_continue))
        }
    }
}

@Composable
private fun WakeWordModelStep(
    accessKeyMasked: String?,
    modelsPresent: Boolean,
    onAccessKeySet: (String, Boolean) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    val present by remember(modelsPresent) { mutableStateOf(modelsPresent) }
    val alphaKeyBaked = com.mamy.android.BuildConfig.PICOVOICE_ACCESS_KEY.isNotBlank()

    Column(
        modifier = Modifier.testTag("onboarding-step-wakeword-model"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_wakeword_model_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (alphaKeyBaked) {
            Text(
                stringResource(R.string.onboarding_wakeword_builtin_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSkip,
                modifier = Modifier.testTag("onboarding-btn-use-builtin"),
            ) {
                Text(stringResource(R.string.onboarding_btn_use_builtin))
            }
        } else {
            Text(
                stringResource(R.string.onboarding_wakeword_model_steps),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.onboarding_wakeword_accesskey_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.testTag("onboarding-wakeword-accesskey"),
            )
            accessKeyMasked?.let {
                Text(
                    stringResource(R.string.onboarding_wakeword_accesskey_set, it),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(
                onClick = { onAccessKeySet(key, present) },
                enabled = key.isNotBlank(),
                modifier = Modifier.testTag("onboarding-btn-save-accesskey"),
            ) {
                Text(stringResource(R.string.onboarding_btn_save))
            }
            Button(
                onClick = onContinue,
                enabled = accessKeyMasked != null,
                modifier = Modifier.testTag("onboarding-btn-continue"),
            ) {
                Text(stringResource(R.string.onboarding_btn_continue))
            }
        }
    }
}

/**
 * SMS opt-in step. NEW from P9.
 *
 * Two paths :
 * - User toggles SMS on → triggers READ_CONTACTS + SEND_SMS permission request
 *   in the host activity (driven by the screen via [onOptIn]) → continues
 * - User taps "Plus tard" → [onSkip] sets opt-in = false and advances to Calendar
 */
@Composable
private fun SmsStep(
    optIn: Boolean,
    permissionsGranted: Boolean,
    onOptIn: (Boolean, Boolean) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("onboarding-step-sms"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_sms_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.onboarding_sms_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.onboarding_sms_perms_explain),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOptIn(true, true) },
                modifier = Modifier.testTag("onboarding-btn-sms-enable"),
            ) {
                Text(stringResource(R.string.onboarding_sms_btn_enable))
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.testTag("onboarding-btn-sms-skip"),
            ) {
                Text(stringResource(R.string.onboarding_sms_btn_skip))
            }
        }
        if (optIn && permissionsGranted) {
            Button(
                onClick = onContinue,
                modifier = Modifier.testTag("onboarding-btn-continue"),
            ) {
                Text(stringResource(R.string.onboarding_btn_continue))
            }
        }
    }
}

@Composable
private fun CalendarStep(
    account: String?,
    onConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.testTag("onboarding-step-calendar"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_calendar_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        account?.let {
            Text(stringResource(R.string.onboarding_calendar_connected_as, it))
        }
        Button(
            onClick = onConnect,
            modifier = Modifier.testTag("onboarding-btn-connect-calendar"),
        ) {
            Text(stringResource(R.string.onboarding_btn_connect_google))
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.testTag("onboarding-btn-skip-calendar"),
        ) {
            Text(stringResource(R.string.onboarding_btn_skip))
        }
    }
}

@Composable
private fun WakeWordStep(onTest: () -> Unit, isLoading: Boolean) {
    Column(
        modifier = Modifier.testTag("onboarding-step-wakeword"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_wakeword_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onTest,
            enabled = !isLoading,
            modifier = Modifier.testTag("onboarding-btn-test-wakeword"),
        ) {
            Text(stringResource(R.string.onboarding_btn_test_wakeword))
        }
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.testTag("onboarding-step-done"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_done_body),
            style = MaterialTheme.typography.headlineSmall,
        )
        Button(
            onClick = onFinish,
            modifier = Modifier.testTag("onboarding-btn-finish"),
        ) {
            Text(stringResource(R.string.onboarding_btn_finish))
        }
    }
}
