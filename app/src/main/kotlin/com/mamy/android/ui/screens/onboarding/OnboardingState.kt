package com.mamy.android.ui.screens.onboarding

import com.mamy.android.ui.onboarding.contracts.OnboardingLlmProvider

/**
 * Onboarding flow steps, in order.
 *
 * V1.5 alpha removed the Byok step (default LLM is local Ollama via Cloudflare
 * Tunnel — no API key required). BYOK power users configure Claude/OpenAI/Gemini
 * later in Settings.
 *
 * Step 3 (Sms) is from P9 — opt-in vocal SMS with READ_CONTACTS + SEND_SMS.
 */
enum class OnboardingStep {
    Permissions,   // Step 1 : RECORD_AUDIO + FOREGROUND_SERVICE_MICROPHONE + POST_NOTIFICATIONS
    WakeWordModel, // Step 2 : Picovoice AccessKey (or "use built-in JARVIS" skip)
    Sms,           // Step 3 : opt-in SMS vocal (READ_CONTACTS + SEND_SMS)
    Calendar,      // Step 4 : Google Calendar OAuth (optional)
    WakeWord,      // Step 5 : test "Jarvis"
    Done,          // Step 6 : finish → ReportsList
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
