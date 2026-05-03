package com.mamy.android.data.calendar.google

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CalendarTokenStore @Inject constructor(
    @Named("calendar_prefs") private val prefs: SharedPreferences,
    private val json: Json
) {

    suspend fun load(): CalendarTokens? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY, null) ?: return@withContext null
        runCatching { json.decodeFromString(CalendarTokens.serializer(), raw) }.getOrNull()
    }

    suspend fun save(tokens: CalendarTokens): Unit = withContext(Dispatchers.IO) {
        val raw = json.encodeToString(CalendarTokens.serializer(), tokens)
        prefs.edit { putString(KEY, raw) }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "calendar_tokens"
    }
}
