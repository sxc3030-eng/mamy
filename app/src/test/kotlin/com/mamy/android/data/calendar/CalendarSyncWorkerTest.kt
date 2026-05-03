package com.mamy.android.data.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarSyncWorkerTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `success when delta sync succeeds`() = runBlocking {
        val delta = mockk<DeltaCalendarSyncUseCase>()
        coEvery { delta.execute("primary") } returns kotlin.Result.success(Unit)

        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(ctx)
            .setWorkerFactory(TestCalendarSyncWorkerFactory(delta))
            .build()

        val result = worker.startWork().get()
        assertEquals(Result.success(), result)
    }

    @Test
    fun `retry when delta sync fails with transient error`() = runBlocking {
        val delta = mockk<DeltaCalendarSyncUseCase>()
        coEvery { delta.execute(any()) } returns kotlin.Result.failure(RuntimeException("network"))

        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(ctx)
            .setWorkerFactory(TestCalendarSyncWorkerFactory(delta))
            .build()

        val result = worker.startWork().get()
        assertEquals(Result.retry(), result)
    }
}

private class TestCalendarSyncWorkerFactory(
    private val delta: DeltaCalendarSyncUseCase
) : androidx.work.WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): androidx.work.ListenableWorker = CalendarSyncWorker(appContext, workerParameters, delta)
}
