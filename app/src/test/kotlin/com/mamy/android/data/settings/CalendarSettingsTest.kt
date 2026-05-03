package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarSettingsTest {

    @Test
    fun `default calendar_enabled is false`() = runTest {
        val store = mockk<DataStore<Preferences>>()
        coEvery { store.data } returns MutableStateFlow(preferencesOf())
        val settings = CalendarSettings(store)
        assertEquals(false, settings.isCalendarEnabled.first())
    }

    @Test
    fun `setCalendarEnabled writes flag`() = runTest {
        val store = mockk<DataStore<Preferences>>(relaxed = true)
        val settings = CalendarSettings(store)
        settings.setCalendarEnabled(true)
        coVerify { store.updateData(any()) }
    }
}
