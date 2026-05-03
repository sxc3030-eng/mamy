package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryBriefingTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storeFile = File(ctx.cacheDir, "test-settings-${System.nanoTime()}.preferences_pb")
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create { storeFile }
    private val sut = SettingsRepository(dataStore)

    @After
    fun cleanup() {
        storeFile.delete()
    }

    @Test
    fun `daily brief time round trips`() = runBlocking {
        sut.setDailyBriefTime(7, 30)
        val snap = sut.snapshot()
        assertEquals(7, snap.dailyBriefingHour)
        assertEquals(30, snap.dailyBriefingMinute)
    }

    @Test
    fun `tts rate clamps at 0_5`() = runBlocking {
        sut.setTtsRate(0.1f)
        assertEquals(0.5f, sut.snapshot().ttsRate, 0.001f)
    }

    @Test
    fun `tts rate clamps at 2_0`() = runBlocking {
        sut.setTtsRate(5f)
        assertEquals(2.0f, sut.snapshot().ttsRate, 0.001f)
    }

    @Test
    fun `locale null clears stored tag`() = runBlocking {
        sut.setLocale("fr-CA")
        sut.setLocale(null)
        assertNull(sut.snapshot().locale)
    }
}
