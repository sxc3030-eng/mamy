package com.mamy.android.service.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.mamy.android.data.settings.SettingsRepository
import com.mamy.android.data.settings.SettingsSnapshot
import com.mamy.android.domain.briefing.BriefingGenerator
import com.mamy.android.domain.briefing.BriefingResult
import com.mamy.android.service.notif.BriefingNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DailyBriefingWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Build a [DailyBriefingWorker] under test via WorkManager's testing helper. We
     * pass our mocks through a [WorkerFactory] so the constructor receives the
     * intended deps without us needing to fabricate a [WorkerParameters].
     */
    private fun newWorker(
        gen: BriefingGenerator,
        notifier: BriefingNotifier,
        settings: SettingsRepository,
        clock: Clock,
    ): DailyBriefingWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = DailyBriefingWorker(appContext, workerParameters, gen, notifier, settings, clock)
        }
        return TestListenableWorkerBuilder<DailyBriefingWorker>(context)
            .setWorkerFactory(factory)
            .build() as DailyBriefingWorker
    }

    @Test
    fun `outside window returns success without calling generator`() = runBlocking {
        val now = Instant.parse("2026-05-02T18:00:00Z") // not 08:00
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val gen = mockk<BriefingGenerator>()
        val notifier = mockk<BriefingNotifier>(relaxed = true)
        val settings = mockk<SettingsRepository>()
        coEvery { settings.snapshot() } returns SettingsSnapshot(
            dailyBriefingHour = 8, dailyBriefingMinute = 0,
            locale = null, ttsRate = 1f, zoneId = ZoneId.of("UTC"),
        )
        val sut = newWorker(gen, notifier, settings, clock)
        val res = sut.doWork()
        assertEquals(Result.success(), res)
        coVerify(exactly = 0) { gen.generate(any()) }
    }

    @Test
    fun `inside window generates and posts notif`() = runBlocking {
        val now = Instant.parse("2026-05-02T08:30:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val gen = mockk<BriefingGenerator>()
        val notifier = mockk<BriefingNotifier>(relaxed = true)
        val settings = mockk<SettingsRepository>()
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, null, 1f, ZoneId.of("UTC"))
        coEvery { gen.generate(any()) } returns BriefingResult(
            "Hello", now, now.plusSeconds(8 * 3600), false, "claude", 3,
        )

        val sut = newWorker(gen, notifier, settings, clock)
        val res = sut.doWork()

        assertEquals(Result.success(), res)
        coVerify { gen.generate(any()) }
        coVerify { notifier.postDailyReady(any()) }
    }

    @Test
    fun `generator failure returns retry`() = runBlocking {
        val now = Instant.parse("2026-05-02T08:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val gen = mockk<BriefingGenerator>()
        val notifier = mockk<BriefingNotifier>(relaxed = true)
        val settings = mockk<SettingsRepository>()
        coEvery { settings.snapshot() } returns SettingsSnapshot(8, 0, null, 1f, ZoneId.of("UTC"))
        coEvery { gen.generate(any()) } throws IllegalStateException("LLM down")

        val sut = newWorker(gen, notifier, settings, clock)
        val res = sut.doWork()

        assertEquals(Result.retry(), res)
    }
}
