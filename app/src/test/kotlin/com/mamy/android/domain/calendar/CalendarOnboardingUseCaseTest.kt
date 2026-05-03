package com.mamy.android.domain.calendar

import com.mamy.android.data.calendar.InitialCalendarSyncUseCase
import com.mamy.android.data.calendar.CalendarSyncScheduler
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarOnboardingUseCaseTest {

    private val auth = mockk<CalendarAuthManager>(relaxed = true)
    private val initial = mockk<InitialCalendarSyncUseCase>(relaxed = true)
    private val scheduler = mockk<CalendarSyncScheduler>(relaxed = true)
    private val settings = mockk<CalendarSettings>(relaxed = true)
    private val useCase = CalendarOnboardingUseCase(auth, initial, scheduler, settings)

    @Test
    fun `completeOnboarding chains - persist tokens, initial sync, schedule, set flag`() = runTest {
        coEvery { auth.completeAuthorization("code-1", "marc@x.com", any()) } returns mockk(relaxed = true)
        coEvery { initial.execute("primary", any(), any()) } returns Result.success(Unit)

        val result = useCase.completeOnboarding(
            authCode = "code-1",
            accountEmail = "marc@x.com",
            scope = "https://www.googleapis.com/auth/calendar.readonly"
        )

        assertTrue(result.isSuccess)
        coVerify { initial.execute("primary", any(), any()) }
        coVerify { scheduler.schedulePeriodic() }
        coVerify { settings.setCalendarEnabled(true) }
    }

    @Test
    fun `disconnect clears auth, cancels scheduler, sets flag false`() = runTest {
        useCase.disconnect()
        coVerify { auth.signOut() }
        coVerify { scheduler.cancelPeriodic() }
        coVerify { settings.setCalendarEnabled(false) }
    }

    @Test
    fun `completeOnboarding returns failure if auth exchange fails`() = runTest {
        coEvery { auth.completeAuthorization(any(), any(), any()) } returns null
        val result = useCase.completeOnboarding("bad", "e@x.com", "s")
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { initial.execute(any(), any(), any()) }
    }
}
