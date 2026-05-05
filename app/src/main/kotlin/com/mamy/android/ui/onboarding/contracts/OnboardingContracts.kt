package com.mamy.android.ui.onboarding.contracts

import kotlinx.coroutines.flow.Flow

/**
 * Onboarding-only LLM provider catalog. Mirrors [com.mamy.android.data.llm.LlmProviderId]
 * constants but uses a Kotlin enum so the picker UI can iterate values.
 *
 * Kept inside the onboarding scope (W1-A) so we don't collide with the existing
 * `LlmProvider` interface in `data/llm/`. A later refactor can unify the two.
 */
enum class OnboardingLlmProvider {
    Claude, OpenAi, Gemini, Local
}

/** Result of `BYOKManager.testKey`. */
sealed interface TestResult {
    data object Ok : TestResult
    data class Failed(val reason: String) : TestResult
}

/** Result of `CalendarRepository.connectGoogle`. */
sealed interface OAuthResult {
    data class Success(val email: String) : OAuthResult
    data class Failure(val message: String) : OAuthResult
    data object Cancelled : OAuthResult
}

/**
 * Validates a BYOK key by performing a minimal handshake with the provider.
 * Implementation is provided by P3 (LLM Structurer).
 */
interface BYOKManager {
    fun testKey(provider: OnboardingLlmProvider, key: String): Flow<TestResult>
}

/**
 * Connect Google Calendar via OAuth2 (Credentials Manager). Returns a Flow
 * because the OAuth flow can emit several states (started, success, failure).
 * Implementation is provided by P5 (Calendar).
 */
interface OnboardingCalendarRepository {
    fun connectGoogle(): Flow<OAuthResult>
}

/**
 * Drives a single wake-word firing for the test step. Returns true once the
 * user said « MamY » within a timeout window. Implementation reuses the P2
 * Porcupine engine.
 */
interface WakeWordTester {
    fun testFire(): Flow<Boolean>
}
