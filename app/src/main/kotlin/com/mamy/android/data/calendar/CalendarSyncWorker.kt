package com.mamy.android.data.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deltaSync: DeltaCalendarSyncUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val calendarId = inputData.getString(KEY_CALENDAR_ID) ?: "primary"
        return deltaSync.execute(calendarId).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val KEY_CALENDAR_ID = "calendar_id"
        const val UNIQUE_NAME = "mamy_calendar_periodic"
    }
}
