package com.mamy.android.data.calendar.google

import com.mamy.android.data.network.NetworkLogEntry
import com.mamy.android.data.network.NetworkLogStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.time.Instant
import javax.inject.Inject

class CalendarHttpLogger @Inject constructor(
    private val store: NetworkLogStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val started = Instant.now()
        val response = chain.proceed(req)
        runBlocking {
            store.append(
                NetworkLogEntry(
                    category = NetworkLogEntry.Category.CALENDAR,
                    timestamp = started,
                    method = req.method,
                    url = req.url.toString(),
                    statusCode = response.code,
                    durationMs = Instant.now().toEpochMilli() - started.toEpochMilli()
                )
            )
        }
        return response
    }
}
