package com.mamy.android.ui.screens.onboarding

import com.mamy.android.ui.onboarding.contracts.OnboardingLlmProvider

/**
 * Onboarding flow steps, in order.
 *
 * Step 4 (Sms) is **NEW from P9** — opt-in for vocal SMS feature with
 * [READ_CONTACTS] + [SEND_SMS] permissions and a "Plus tard" skip option.
 */
enum class OnboardingStep {
    Permissions,   // Step 1 : RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE + POST_NOTIFICATIONS
    WakeWordModel, // Step 2 : Picovoice AccessKey + drop ppn files
    Byok,          // Step 3 : BYOK provider + key
    Sms,           // Step 4 : NEW P9 — opt-in SMS vocal (READ_CONTACTS + SEND_SMS)
    Calendar,      // Step 5 : Google Calendar OAuth (optional)
    WakeWord,      // Step 6 : test "MamY test 1 2 3"
    Done,          // Step 7 : finish → ReportsList
}

/**
 * Snapshot of the onboarding flow.
 *
 * Each step gates `next()` until its specific input/test passes (see
 * [OnboardingViewModel]). [errorMessage] is a sticky surface for inline
 * error text; cleared by `next()` and `back()`.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Permissions,
    /** Step 1 — granted? */
    val permissionsGranted: Boolean = false,
    /** Step 2 — Picovoice AccessKey, masked when present. */
    val picovoiceAccessKeyMasked: String? = null,
    /** Step 2 — both `mamy_en.ppn` + `mamy_fr.ppn` detected in app assets. */
    val wakeWordModelsPresent: Boolean = false,
    /** Step 3 — selected BYOK provider. */
    val byokProvider: OnboardingLlmProvider? = null,
    /** Step 3 — masked API key after successful test. */
    val byokKeyMasked: String? = null,
    /** Step 4 — user opted into SMS vocal (NEW P9). */
    val smsOptIn: Boolean = false,
    /** Step 4 — READ_CONTACTS + SEND_SMS granted. */
    val smsPermissionsGranted: Boolean = false,
    /** Step 5 — connected calendar account email, null if skipped. */
    val calendarAccount: String? = null,
    /** Step 6 — wake-word fired successfully. */
    val wakeWordTested: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)
