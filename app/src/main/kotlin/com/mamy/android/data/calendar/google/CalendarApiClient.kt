package com.mamy.android.data.calendar.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class CalendarSyncTokenInvalidException : IOException("sync token expired (410)")
class CalendarAuthExpiredException : IOException("authentication expired and refresh failed")

@Singleton
class CalendarApiClient @Inject constructor(
    @Named("calendar_http_raw") private val httpClient: OkHttpClient,
    private val authManager: CalendarAuthManager,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    suspend fun listEvents(
        calendarId: String,
        timeMin: Instant?,
        timeMax: Instant?,
        syncToken: String?,
        pageToken: String?,
        pageSize: Int = 2500
    ): Result<CalendarEventsList> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/calendars/$calendarId/events".toHttpUrl().newBuilder().apply {
            addQueryParameter("singleEvents", "true")
            addQueryParameter("orderBy", "startTime")
            addQueryParameter("maxResults", pageSize.toString())
            if (syncToken != null) {
                addQueryParameter("syncToken", syncToken)
            } else {
                if (timeMin != null) addQueryParameter("timeMin", DateTimeFormatter.ISO_INSTANT.format(timeMin))
                if (timeMax != null) addQueryParameter("timeMax", DateTimeFormatter.ISO_INSTANT.format(timeMax))
            }
            if (pageToken != null) addQueryParameter("pageToken", pageToken)
        }.build()

        val firstToken = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(CalendarAuthExpiredException())

        val firstResp = runCatching {
            httpClient.newCall(buildAuthorizedRequest(url.toString(), firstToken)).execute()
        }.getOrElse { return@withContext Result.failure(it) }

        firstResp.use { resp ->
            when (resp.code) {
                in 200..299 -> {
                    val body = resp.body?.string() ?: ""
                    return@withContext runCatching {
                        json.decodeFromString(CalendarEventsList.serializer(), body)
                    }
                }
                401 -> {
                    val refreshed = authManager.refreshAccessToken()?.accessToken
                        ?: return@withContext Result.failure(CalendarAuthExpiredException())
                    val retry = runCatching {
                        httpClient.newCall(buildAuthorizedRequest(url.toString(), refreshed)).execute()
                    }.getOrElse { return@withContext Result.failure(it) }
                    retry.use { r2 ->
                        return@withContext when (r2.code) {
                            in 200..299 -> runCatching {
                                json.decodeFromString(CalendarEventsList.serializer(), r2.body?.string() ?: "")
                            }
                            410 -> Result.failure(CalendarSyncTokenInvalidException())
                            else -> Result.failure(IOException("HTTP ${r2.code} on retry"))
                        }
                    }
                }
                410 -> return@withContext Result.failure(CalendarSyncTokenInvalidException())
                else -> return@withContext Result.failure(IOException("HTTP ${resp.code}"))
            }
        }
    }

    private fun buildAuthorizedRequest(url: String, accessToken: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()

    private companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com/calendar/v3"
    }
}
