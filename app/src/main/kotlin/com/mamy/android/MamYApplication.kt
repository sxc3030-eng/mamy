package com.mamy.android

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mamy.android.data.calendar.CalendarSyncScheduler
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MamYApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var calendarSettings: CalendarSettings
    @Inject lateinit var calendarSyncScheduler: CalendarSyncScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Install the crash reporter as the very first thing the app does.
     * This runs BEFORE Hilt's `onCreate`, so even crashes during DI graph
     * construction are captured and POSTed to the i5 proxy at /api/crash.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        try {
            val ctx = base ?: this
            Thread.setDefaultUncaughtExceptionHandler(
                CrashReporter(ctx, Thread.getDefaultUncaughtExceptionHandler())
            )
        } catch (_: Throwable) {
            // never block app startup on crash-reporter install failure
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            calendarSettings.isCalendarEnabled.collect { enabled ->
                if (enabled) calendarSyncScheduler.schedulePeriodic()
                else calendarSyncScheduler.cancelPeriodic()
            }
        }
    }
}
