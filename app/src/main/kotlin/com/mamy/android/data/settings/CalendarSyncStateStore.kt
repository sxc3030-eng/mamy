package com.mamy.android.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CalendarSyncStateStore @Inject constructor(
    @Named("calendar_prefs") private val prefs: SharedPreferences
) {
    fun loadSyncToken(calendarId: String): String? = prefs.getString("sync_token_$calendarId", null)
    fun saveSyncToken(calendarId: String, token: String) =
        prefs.edit { putString("sync_token_$calendarId", token) }
    fun clearSyncToken(calendarId: String) =
        prefs.edit { remove("sync_token_$calendarId") }
}
