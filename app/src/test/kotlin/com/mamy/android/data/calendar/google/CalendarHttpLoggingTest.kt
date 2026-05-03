package com.mamy.android.data.calendar.google

import com.mamy.android.data.network.NetworkLogEntry
import com.mamy.android.data.network.NetworkLogStore
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarHttpLoggingTest {

    private lateinit var server: MockWebServer
    private lateinit var store: NetworkLogStore

    @BeforeEach
    fun setup() {
        server = MockWebServer().apply { start() }
        store = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() = server.shutdown()

    @Test
    fun `interceptor logs calendar API requests with category CALENDAR`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        val client = OkHttpClient.Builder()
            .addInterceptor(CalendarHttpLogger(store))
            .build()
        client.newCall(
            Request.Builder().url(server.url("/calendars/primary/events")).build()
        ).execute().use {}

        val captured = slot<NetworkLogEntry>()
        coVerify { store.append(capture(captured)) }
        assertEquals(NetworkLogEntry.Category.CALENDAR, captured.captured.category)
        assertEquals(200, captured.captured.statusCode)
    }
}
