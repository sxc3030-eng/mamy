package com.mamy.android.di

import com.mamy.android.ui.onboarding.contracts.BYOKManager
import com.mamy.android.ui.onboarding.contracts.OAuthResult
import com.mamy.android.ui.onboarding.contracts.OnboardingCalendarRepository
import com.mamy.android.ui.onboarding.contracts.OnboardingLlmProvider
import com.mamy.android.ui.onboarding.contracts.TestResult
import com.mamy.android.ui.onboarding.contracts.WakeWordTester
import com.mamy.android.ui.screens.reports.PersonRow
import com.mamy.android.ui.screens.reports.ReportsPersonRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Singleton

/**
 * DI bindings for the onboarding/reports UI contracts (W1-A).
 *
 * Provides safe stub implementations so the Hilt graph compiles before the
 * real P3/P5/P2 wiring lands in subsequent waves. Each stub returns an empty
 * or "not yet wired" Flow rather than throwing — ViewModels handle that
 * gracefully (success path won't trigger, screens stay on the current step).
 *
 * The orchestrator should swap these for real bindings once the producing
 * agents (P3 BYOK, P5 Calendar OAuth, P2 wake-word tester) have shipped.
 */
@Module
@InstallIn(SingletonComponent::class)
object OnboardingUiModule {

    @Provides
    @Singleton
    fun provideOnboardingCalendarRepository(): OnboardingCalendarRepository =
        object : OnboardingCalendarRepository {
            override fun connectGoogle(): Flow<OAuthResult> =
                flowOf(OAuthResult.Failure("calendar_not_wired_yet"))
        }

    @Provides
    @Singleton
    fun provideBYOKManager(): BYOKManager = object : BYOKManager {
        override fun testKey(provider: OnboardingLlmProvider, key: String): Flow<TestResult> =
            flowOf(TestResult.Failed("byok_not_wired_yet"))
    }

    @Provides
    @Singleton
    fun provideWakeWordTester(): WakeWordTester = object : WakeWordTester {
        override fun testFire(): Flow<Boolean> = flowOf(false)
    }

    @Provides
    @Singleton
    fun provideReportsPersonRepository(): ReportsPersonRepository =
        object : ReportsPersonRepository {
            override fun observeAll(filterUnmatched: Boolean): Flow<List<PersonRow>> =
                flowOf(emptyList())
        }
}
