package com.mamy.android.data.calendar.google

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var auth: CalendarAuthManager
    private lateinit var client: CalendarApiClient

    @BeforeEach
    fun setup() {
        server = MockWebServer().apply { start() }
        auth = mockk(relaxed = true)
        coEvery { auth.getValidAccessToken() } returns "ya29.first"
        client = CalendarApiClient(
            httpClient = OkHttpClient(),
            authManager = auth,
            json = Json { ignoreUnknownKeys = true },
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @AfterEach
    fun teardown() = server.shutdown()

    @Test
    fun `listEvents adds Authorization header and parses response`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"items":[{"id":"e1","summary":"T","start":{"dateTime":"2026-05-02T10:00:00Z"},"end":{"dateTime":"2026-05-02T10:30:00Z"}}],"nextSyncToken":"sync-1"}"""
        ))
        val res = client.listEvents(
            calendarId = "primary",
            timeMin = Instant.parse("2026-05-01T00:00:00Z"),
            timeMax = Instant.parse("2026-06-01T00:00:00Z"),
            syncToken = null,
            pageToken = null
        )
        assertTrue(res.isSuccess)
        assertEquals("e1", res.getOrThrow().items.first().id)
        assertEquals("sync-1", res.getOrThrow().nextSyncToken)

        val recorded = server.takeRequest()
        assertEquals("Bearer ya29.first", recorded.getHeader("Authorization"))
        assertTrue(recorded.path!!.contains("/calendars/primary/events"))
        assertTrue(recorded.path!!.contains("singleEvents=true"))
        assertTrue(recorded.path!!.contains("orderBy=startTime"))
    }

    @Test
    fun `listEvents retries once after 401 with refreshed token`() = runTest {
        coEvery { auth.getValidAccessToken() } returnsMany listOf("expired", "fresh")
        coEvery { auth.refreshAccessToken() } returns CalendarTokens(
            "fresh", "r", System.currentTimeMillis() + 3600_000, "s", "e@x.com"
        )
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"items":[],"nextSyncToken":"s2"}"""))
        val res = client.listEvents("primary", null, null, null, null)
        assertTrue(res.isSuccess)
        assertEquals("s2", res.getOrThrow().nextSyncToken)

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer expired", first.getHeader("Authorization"))
        assertEquals("Bearer fresh", second.getHeader("Authorization"))
    }

    @Test
    fun `listEvents fails after second 401 (refresh failed)`() = runTest {
        coEvery { auth.refreshAccessToken() } returns null
        server.enqueue(MockResponse().setResponseCode(401))
        val res = client.listEvents("primary", null, null, null, null)
        assertTrue(res.isFailure)
    }

    @Test
    fun `listEvents propagates 410 syncTokenInvalid`() = runTest {
        server.enqueue(MockResponse().setResponseCode(410).setBody(
            """{"error":{"code":410,"message":"Sync token is no longer valid"}}"""
        ))
        val res = client.listEvents("primary", null, null, syncToken = "stale", null)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is CalendarSyncTokenInvalidException)
    }

    @Test
    fun `listEvents includes syncToken when provided and omits time bounds`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[],"nextSyncToken":"s3"}"""))
        client.listEvents("primary", null, null, syncToken = "abc", null)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("syncToken=abc"))
        // When syncToken is supplied, timeMin/timeMax must not be sent.
        assertTrue(!req.path!!.contains("timeMin"))
    }
}
