package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keyEnabled = booleanPreferencesKey("calendar_enabled")

    val isCalendarEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyEnabled] ?: false }

    suspend fun setCalendarEnabled(enabled: Boolean) {
        dataStore.edit { it[keyEnabled] = enabled }
    }
}
