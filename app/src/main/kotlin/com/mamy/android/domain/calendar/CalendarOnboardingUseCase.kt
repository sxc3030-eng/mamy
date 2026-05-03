package com.mamy.android.domain.calendar

import com.mamy.android.data.calendar.CalendarSyncScheduler
import com.mamy.android.data.calendar.InitialCalendarSyncUseCase
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import javax.inject.Inject

class CalendarOnboardingUseCase @Inject constructor(
    private val authManager: CalendarAuthManager,
    private val initialSync: InitialCalendarSyncUseCase,
    private val scheduler: CalendarSyncScheduler,
    private val settings: CalendarSettings
) {

    suspend fun completeOnboarding(
        authCode: String,
        accountEmail: String,
        scope: String
    ): Result<Unit> {
        val tokens = authManager.completeAuthorization(authCode, accountEmail, scope)
            ?: return Result.failure(IllegalStateException("authorization exchange failed"))
        val sync = initialSync.execute()
        if (sync.isFailure) return sync
        scheduler.schedulePeriodic()
        settings.setCalendarEnabled(true)
        return Result.success(Unit)
    }

    suspend fun disconnect() {
        scheduler.cancelPeriodic()
        authManager.signOut()
        settings.setCalendarEnabled(false)
    }
}
