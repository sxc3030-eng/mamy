package com.mamy.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mamy.android.data.calendar.CalendarSyncScheduler
import com.mamy.android.data.settings.CalendarSettings
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
