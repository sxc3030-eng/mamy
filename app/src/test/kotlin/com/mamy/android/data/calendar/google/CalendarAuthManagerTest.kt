package com.mamy.android.data.calendar.google

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CalendarAuthManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var store: CalendarTokenStore
    private lateinit var manager: CalendarAuthManager
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)

    @BeforeEach
    fun setup() {
        server = MockWebServer().apply { start() }
        store = mockk(relaxed = true)
        manager = CalendarAuthManager(
            tokenStore = store,
            httpClient = OkHttpClient(),
            tokenEndpoint = server.url("/oauth2/v4/token").toString(),
            webClientId = "web-client-id.apps.googleusercontent.com",
            clock = fixedClock
        )
    }

    @AfterEach
    fun teardown() = server.shutdown()

    @Test
    fun `refreshAccessToken posts refresh_token grant and saves new tokens`() = runTest {
        coEvery { store.load() } returns CalendarTokens(
            accessToken = "old", refreshToken = "1//refresh-x",
            expiresAt = 0L, scope = "s", accountEmail = "marc@x.com"
        )
        server.enqueue(MockResponse().setBody(
            """{"access_token":"new-access","expires_in":3600,"scope":"s","token_type":"Bearer"}"""
        ))

        val result = manager.refreshAccessToken()

        assertNotNull(result)
        assertEquals("new-access", result!!.accessToken)
        assertEquals("1//refresh-x", result.refreshToken)
        // expiresAt = now (fixed) + 3600 sec
        assertEquals(
            Instant.parse("2026-05-02T12:00:00Z").plusSeconds(3600).toEpochMilli(),
            result.expiresAt
        )
        coVerify { store.save(match { it.accessToken == "new-access" }) }

        val recorded = server.takeRequest()
        assertEquals("/oauth2/v4/token", recorded.path)
        val body = recorded.body.readUtf8()
        assert(body.contains("grant_type=refresh_token"))
        assert(body.contains("refresh_token=1%2F%2Frefresh-x"))
    }

    @Test
    fun `refreshAccessToken returns null when no stored refresh token`() = runTest {
        coEvery { store.load() } returns null
        assertEquals(null, manager.refreshAccessToken())
    }

    @Test
    fun `refreshAccessToken returns null on 400 invalid_grant`() = runTest {
        coEvery { store.load() } returns CalendarTokens(
            "old", "1//bad", 0L, "s", "e@x.com"
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"error":"invalid_grant","error_description":"Token has been expired or revoked."}"""
        ))
        assertEquals(null, manager.refreshAccessToken())
    }

    @Test
    fun `getValidAccessToken refreshes when expired`() = runTest {
        val expired = CalendarTokens(
            "old", "1//r",
            expiresAt = Instant.parse("2026-05-02T11:00:00Z").toEpochMilli(),
            scope = "s", accountEmail = "e@x.com"
        )
        coEvery { store.load() } returns expired
        server.enqueue(MockResponse().setBody(
            """{"access_token":"fresh","expires_in":3600,"scope":"s","token_type":"Bearer"}"""
        ))
        val token = manager.getValidAccessToken()
        assertEquals("fresh", token)
    }

    @Test
    fun `getValidAccessToken returns cached when not expired`() = runTest {
        val good = CalendarTokens(
            "still-good", "1//r",
            expiresAt = Instant.parse("2026-05-02T13:00:00Z").toEpochMilli(),
            scope = "s", accountEmail = "e@x.com"
        )
        coEvery { store.load() } returns good
        assertEquals("still-good", manager.getValidAccessToken())
        // No server hit expected; nothing was enqueued.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `signOut clears store`() = runTest {
        manager.signOut()
        coVerify { store.clear() }
    }
}
