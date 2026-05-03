# MamY P5 — Calendar Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Google Calendar OAuth, periodic delta sync via WorkManager, automatic person matching from event attendees, and graceful no-calendar fallback. After P5, calendar events appear as Meeting rows in DB with attendees mapped to Person rows, ready for P6 to generate briefings.

**Architecture:** AndroidX Credentials API for OAuth, OkHttp client with auto-refresh interceptor, Google Calendar v3 REST API, WorkManager periodic 15-min delta sync via `syncToken`. Person matching uses email as primary key, falls back to creating `unmatched=true` stubs for user confirmation.

**Tech Stack:** AndroidX Credentials 1.5 · Google Sign-In API · OkHttp 4.12 · WorkManager 2.10 · kotlinx.serialization 1.7

---

## Pre-flight assumptions

- P1 shipped : `MamYDatabase`, all entities (`Person`, `Meeting`, `MeetingAttendee`, `Note`, `Action`, `Promise`, `Flag`, `Briefing`), Hilt DI, `data/secrets/` with `EncryptedSharedPrefsModule`, `data/settings/` with DataStore.
- P2 shipped : `MamYListenerService` foreground service skeleton (no OAuth dependency).
- P3 shipped : `LlmProvider` sealed interface, BYOK key storage in Android Keystore (`SecretsVault`).
- P4 shipped : `IntentRouter`, `CaptureUseCase`, `PersonRepository.upsertByName(...)` (used by manual capture path).
- Branch base : `main`. Working branch : `p5-calendar`.
- All paths absolute Windows : `D:/ComfyUI-Intel/mamy/app/src/...`.

---

## Task 1 — Add AndroidX Credentials + Google Sign-In + OkHttp deps

- [ ] **1.1** Add versions to `D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml` :

```toml
[versions]
# ... (existing versions kept) ...
credentials = "1.5.0"
google-id = "1.1.1"
play-services-auth = "21.2.0"
okhttp = "4.12.0"
serialization = "1.7.3"

[libraries]
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
androidx-credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
google-id = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "google-id" }
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "play-services-auth" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
```

- [ ] **1.2** Add to `D:/ComfyUI-Intel/mamy/app/build.gradle.kts` plugins block :

```kotlin
plugins {
    // ... existing ...
    alias(libs.plugins.kotlin.serialization)
}
```

- [ ] **1.3** Add to `dependencies` block in `app/build.gradle.kts` :

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services)
implementation(libs.google.id)
implementation(libs.play.services.auth)
implementation(libs.okhttp)
implementation(libs.kotlinx.serialization.json)

testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **1.4** Add to `D:/ComfyUI-Intel/mamy/gradle/libs.versions.toml` plugins block :

```toml
[plugins]
# ... existing ...
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **1.5** Run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` to confirm resolution. Expect no errors.

- [ ] **1.6** Commit : `chore: add AndroidX Credentials + Google Sign-In + OkHttp dependencies for P5 calendar`

---

## Task 2 — Add OAuth client config (Google Cloud Console placeholder)

- [ ] **2.1** Create `D:/ComfyUI-Intel/mamy/app/src/main/res/values/calendar_config.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- TODO before public release: replace with real OAuth client ID from Google Cloud Console -->
    <!-- Type: Android (with SHA-1 fingerprint of signing key) and Web (for server tokens) -->
    <!-- During development, use a debug client tied to the debug keystore SHA-1 -->
    <string name="google_oauth_web_client_id" translatable="false">REPLACE_ME.apps.googleusercontent.com</string>
    <string name="google_calendar_scope" translatable="false">https://www.googleapis.com/auth/calendar.readonly</string>
</resources>
```

- [ ] **2.2** Create `D:/ComfyUI-Intel/mamy/docs/setup/oauth-google-cloud.md` with the operator runbook :

```markdown
# Google Cloud OAuth setup for MamY

1. Go to https://console.cloud.google.com/, create project `mamy-android-prod` (and `mamy-android-dev`).
2. Enable APIs : `Google Calendar API`.
3. OAuth consent screen :
   - User type : External
   - App name : `MamY`
   - Support email : <ops>
   - Scopes : `https://www.googleapis.com/auth/calendar.readonly`
   - Test users (during dev) : add your dogfood Google accounts.
4. Credentials → Create Credentials → OAuth Client ID :
   - Type : Web application (used for `setServerClientId` server token mint)
   - Name : `mamy-web-token-mint`
   - Copy the Client ID into `app/src/main/res/values/calendar_config.xml` `google_oauth_web_client_id`.
5. Credentials → Create Credentials → OAuth Client ID :
   - Type : Android
   - Package name : `com.mamy.android`
   - SHA-1 : output of `keytool -keystore <ks> -list -v` (debug + prod separate entries)
6. Update `calendar_config.xml` after each environment switch.
```

- [ ] **2.3** Commit : `chore: add OAuth client config placeholder + Google Cloud setup runbook`

---

## Task 3 — `CalendarTokens` data class + serialization

- [ ] **3.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/google/CalendarTokensTest.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarTokensTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes round-trip`() {
        val original = CalendarTokens(
            accessToken = "ya29.abc",
            refreshToken = "1//xyz",
            expiresAt = Instant.parse("2026-05-02T14:30:00Z").toEpochMilli(),
            scope = "https://www.googleapis.com/auth/calendar.readonly",
            accountEmail = "marc@example.com"
        )
        val encoded = json.encodeToString(CalendarTokens.serializer(), original)
        val decoded = json.decodeFromString(CalendarTokens.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `isExpired true when expiresAt past`() {
        val tokens = CalendarTokens(
            accessToken = "x", refreshToken = "y",
            expiresAt = Instant.now().minusSeconds(60).toEpochMilli(),
            scope = "s", accountEmail = "e@e.com"
        )
        assertEquals(true, tokens.isExpired(now = Instant.now()))
    }

    @Test
    fun `isExpired false when expiresAt future plus skew`() {
        val tokens = CalendarTokens(
            accessToken = "x", refreshToken = "y",
            expiresAt = Instant.now().plusSeconds(120).toEpochMilli(),
            scope = "s", accountEmail = "e@e.com"
        )
        assertEquals(false, tokens.isExpired(now = Instant.now()))
    }
}
```

- [ ] **3.2** Run `./gradlew :app:testDebugUnitTest --tests "com.mamy.android.data.calendar.google.CalendarTokensTest"`. Expect FAIL (class missing).

- [ ] **3.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/google/CalendarTokens.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CalendarTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,        // epoch millis
    val scope: String,
    val accountEmail: String
) {
    /**
     * Considered expired if [now] is within 60 seconds of [expiresAt] (clock-skew safe).
     */
    fun isExpired(now: Instant = Instant.now(), skewSeconds: Long = 60): Boolean {
        return now.toEpochMilli() >= expiresAt - skewSeconds * 1000
    }
}
```

- [ ] **3.4** Run test again. Expect PASS.

- [ ] **3.5** Commit : `feat: add CalendarTokens with expiry-with-skew check`

---

## Task 4 — `CalendarTokenStore` (EncryptedSharedPreferences wrapper)

- [ ] **4.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/google/CalendarTokenStoreTest.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CalendarTokenStoreTest {

    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val store = CalendarTokenStore(prefs, json)

    @Test
    fun `load returns null when key missing`() = runTest {
        every { prefs.getString("calendar_tokens", null) } returns null
        assertNull(store.load())
    }

    @Test
    fun `load returns parsed tokens when present`() = runTest {
        val tokens = CalendarTokens("a", "r", 1234L, "s", "e@x.com")
        every { prefs.getString("calendar_tokens", null) } returns
            json.encodeToString(CalendarTokens.serializer(), tokens)
        assertEquals(tokens, store.load())
    }

    @Test
    fun `save serializes and commits`() = runTest {
        val tokens = CalendarTokens("a", "r", 1234L, "s", "e@x.com")
        every { prefs.edit() } returns editor
        val captured = slot<String>()
        every { editor.putString("calendar_tokens", capture(captured)) } returns editor

        store.save(tokens)

        val decoded = json.decodeFromString(CalendarTokens.serializer(), captured.captured)
        assertEquals(tokens, decoded)
        verify { editor.apply() }
    }

    @Test
    fun `clear removes key`() = runTest {
        every { prefs.edit() } returns editor
        store.clear()
        verify { editor.remove("calendar_tokens") }
        verify { editor.apply() }
    }
}
```

- [ ] **4.2** Run test. Expect FAIL.

- [ ] **4.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/google/CalendarTokenStore.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarTokenStore @Inject constructor(
    private val prefs: SharedPreferences,
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
```

- [ ] **4.4** Run test. Expect PASS.

- [ ] **4.5** Add Hilt module `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/di/CalendarModule.kt` :

```kotlin
package com.mamy.android.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    @Provides @Singleton @Named("calendar_prefs")
    fun provideCalendarPrefs(@ApplicationContext context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "mamy_calendar_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides @Singleton
    fun provideCalendarJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
```

- [ ] **4.6** Add `androidx.security:security-crypto:1.1.0-alpha06` to `libs.versions.toml` and `app/build.gradle.kts` if not already present from P1.

- [ ] **4.7** Update `CalendarTokenStore` constructor injection annotation to consume `@Named("calendar_prefs")` :

```kotlin
class CalendarTokenStore @Inject constructor(
    @Named("calendar_prefs") private val prefs: SharedPreferences,
    private val json: Json
)
```

- [ ] **4.8** Re-run unit test (mock injection bypasses qualifier). Expect PASS.

- [ ] **4.9** Commit : `feat: add CalendarTokenStore backed by EncryptedSharedPreferences`

---

## Task 5 — `CalendarAuthManager` (sign-in + token refresh)

- [ ] **5.1** Add OkHttp client provider to `CalendarModule` :

```kotlin
@Provides @Singleton @Named("calendar_http_raw")
fun provideRawCalendarHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

Imports needed : `okhttp3.OkHttpClient`, `java.util.concurrent.TimeUnit`.

- [ ] **5.2** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/google/CalendarAuthManagerTest.kt` :

```kotlin
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
```

- [ ] **5.3** Run test. Expect FAIL.

- [ ] **5.4** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/google/CalendarAuthManager.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CalendarAuthManager @Inject constructor(
    @ApplicationContext private val context: Context? = null,
    private val tokenStore: CalendarTokenStore,
    @Named("calendar_http_raw") private val httpClient: OkHttpClient = OkHttpClient(),
    private val tokenEndpoint: String = DEFAULT_TOKEN_ENDPOINT,
    private val webClientId: String = "",
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * Triggers the AndroidX Credentials sign-in flow. Returns the email of the signed-in account
     * on success, or null if user cancelled / error.
     *
     * NOTE: this returns an ID token only; the calendar.readonly authorization is granted via
     * the separate `requestCalendarAuthorization` step which uses Google Identity Authorization API.
     */
    suspend fun signIn(activityContext: Context): String? {
        val ctx = activityContext
        val credentialManager = CredentialManager.create(ctx)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val response = credentialManager.getCredential(ctx, request)
            val cred = response.credential
            if (cred is GoogleIdTokenCredential || cred.javaClass.name.contains("GoogleId")) {
                val idTokenCred = GoogleIdTokenCredential.createFrom(cred.data)
                idTokenCred.id // user email
            } else null
        } catch (e: GetCredentialException) {
            null
        }
    }

    /**
     * After [signIn] succeeds, the host calls this with the auth-code returned from
     * `AuthorizationClient.getAuthorizationResult` (Google Identity Authorization API).
     * Exchanges the code for access + refresh tokens and persists them.
     */
    suspend fun completeAuthorization(
        authCode: String,
        accountEmail: String,
        scope: String
    ): CalendarTokens? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", authCode)
            .add("client_id", webClientId)
            .build()
        val req = Request.Builder().url(tokenEndpoint).post(body).build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val payload = resp.body?.string() ?: return@use null
                val parsed = Json { ignoreUnknownKeys = true }
                    .decodeFromString(TokenResponse.serializer(), payload)
                val refresh = parsed.refreshToken ?: return@use null
                CalendarTokens(
                    accessToken = parsed.accessToken,
                    refreshToken = refresh,
                    expiresAt = clock.instant().plusSeconds(parsed.expiresIn).toEpochMilli(),
                    scope = parsed.scope ?: scope,
                    accountEmail = accountEmail
                ).also { tokenStore.save(it) }
            }
        }.getOrNull()
    }

    /**
     * Returns a still-valid access token, refreshing via refresh_token grant if expired.
     * Returns null if no stored credentials or refresh failed (caller must trigger re-auth).
     */
    suspend fun getValidAccessToken(): String? {
        val tokens = tokenStore.load() ?: return null
        if (!tokens.isExpired(clock.instant())) return tokens.accessToken
        return refreshAccessToken()?.accessToken
    }

    suspend fun refreshAccessToken(): CalendarTokens? = withContext(Dispatchers.IO) {
        val current = tokenStore.load() ?: return@withContext null
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", current.refreshToken)
            .add("client_id", webClientId)
            .build()
        val req = Request.Builder().url(tokenEndpoint).post(body).build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val payload = resp.body?.string() ?: return@use null
                val parsed = Json { ignoreUnknownKeys = true }
                    .decodeFromString(TokenResponse.serializer(), payload)
                val refreshed = current.copy(
                    accessToken = parsed.accessToken,
                    expiresAt = clock.instant().plusSeconds(parsed.expiresIn).toEpochMilli(),
                    scope = parsed.scope ?: current.scope
                )
                tokenStore.save(refreshed)
                refreshed
            }
        }.getOrNull()
    }

    suspend fun signOut() {
        tokenStore.clear()
    }

    suspend fun isSignedIn(): Boolean = tokenStore.load() != null

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val expires_in: Long,
        val refresh_token: String? = null,
        val scope: String? = null,
        val token_type: String? = null
    ) {
        val accessToken: String get() = access_token
        val expiresIn: Long get() = expires_in
        val refreshToken: String? get() = refresh_token
    }

    private companion object {
        const val DEFAULT_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }
}
```

- [ ] **5.5** Run test. Expect PASS.

- [ ] **5.6** Add Hilt provider in `CalendarModule` :

```kotlin
@Provides @Singleton
fun provideCalendarAuthManager(
    @ApplicationContext context: Context,
    tokenStore: CalendarTokenStore,
    @Named("calendar_http_raw") httpClient: OkHttpClient,
    @ApplicationContext appContext: Context
): CalendarAuthManager {
    val webClientId = appContext.getString(
        appContext.resources.getIdentifier("google_oauth_web_client_id", "string", appContext.packageName)
    )
    return CalendarAuthManager(
        context = context,
        tokenStore = tokenStore,
        httpClient = httpClient,
        webClientId = webClientId
    )
}
```

- [ ] **5.7** Commit : `feat: add CalendarAuthManager with sign-in and token refresh via OkHttp`

---

## Task 6 — `CalendarEvent` + `CalendarAttendee` data classes

- [ ] **6.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/google/CalendarApiModelsTest.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalendarApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes minimal event with start and end dateTime`() {
        val raw = """{
            "id": "evt-1",
            "summary": "1:1 Marie",
            "start": {"dateTime": "2026-05-02T10:00:00-04:00"},
            "end":   {"dateTime": "2026-05-02T10:30:00-04:00"},
            "attendees": [
              {"email": "marie@example.com", "displayName": "Marie Dubois", "responseStatus": "accepted"},
              {"email": "marc@example.com", "self": true, "responseStatus": "accepted"}
            ]
        }"""
        val ev = json.decodeFromString(CalendarEvent.serializer(), raw)
        assertEquals("evt-1", ev.id)
        assertEquals("1:1 Marie", ev.summary)
        assertEquals("2026-05-02T10:00:00-04:00", ev.start?.dateTime)
        assertEquals(2, ev.attendees?.size)
        assertEquals("marie@example.com", ev.attendees!![0].email)
        assertEquals(true, ev.attendees!![1].self)
    }

    @Test
    fun `decodes events list response with nextPageToken and nextSyncToken`() {
        val raw = """{
            "items": [],
            "nextPageToken": "page-2",
            "nextSyncToken": "sync-xyz"
        }"""
        val list = json.decodeFromString(CalendarEventsList.serializer(), raw)
        assertEquals(0, list.items.size)
        assertEquals("page-2", list.nextPageToken)
        assertEquals("sync-xyz", list.nextSyncToken)
    }

    @Test
    fun `decodes cancelled event with status only`() {
        val raw = """{"id":"evt-9","status":"cancelled"}"""
        val ev = json.decodeFromString(CalendarEvent.serializer(), raw)
        assertEquals("cancelled", ev.status)
    }
}
```

- [ ] **6.2** Run test. Expect FAIL.

- [ ] **6.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/google/CalendarApiModels.kt` :

```kotlin
package com.mamy.android.data.calendar.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: String,
    val status: String? = null,                 // "confirmed" | "cancelled" | "tentative"
    val summary: String? = null,
    val description: String? = null,
    val start: CalendarTime? = null,
    val end: CalendarTime? = null,
    val attendees: List<CalendarAttendee>? = null,
    val organizer: CalendarOrganizer? = null,
    val recurringEventId: String? = null,
    val updated: String? = null
)

@Serializable
data class CalendarTime(
    val dateTime: String? = null,               // ISO-8601 with offset (timed events)
    val date: String? = null,                   // YYYY-MM-DD (all-day events)
    val timeZone: String? = null
)

@Serializable
data class CalendarAttendee(
    val email: String? = null,
    val displayName: String? = null,
    val self: Boolean? = null,
    val organizer: Boolean? = null,
    val resource: Boolean? = null,
    val responseStatus: String? = null,         // "accepted" | "declined" | "needsAction" | "tentative"
    val optional: Boolean? = null
)

@Serializable
data class CalendarOrganizer(
    val email: String? = null,
    val displayName: String? = null,
    val self: Boolean? = null
)

@Serializable
data class CalendarEventsList(
    val items: List<CalendarEvent> = emptyList(),
    val nextPageToken: String? = null,
    val nextSyncToken: String? = null,
    @SerialName("timeZone") val calendarTimeZone: String? = null
)
```

- [ ] **6.4** Run test. Expect PASS.

- [ ] **6.5** Commit : `feat: add Google Calendar API data classes (Event, Attendee, EventsList)`

---

## Task 7 — `CalendarApiClient` with auth interceptor + 401 retry

- [ ] **7.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/google/CalendarApiClientTest.kt` :

```kotlin
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
```

- [ ] **7.2** Run test. Expect FAIL.

- [ ] **7.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/google/CalendarApiClient.kt` :

```kotlin
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
```

- [ ] **7.4** Run test. Expect PASS.

- [ ] **7.5** Add Hilt provider to `CalendarModule` :

```kotlin
@Provides @Singleton
fun provideCalendarApiClient(
    @Named("calendar_http_raw") httpClient: OkHttpClient,
    authManager: CalendarAuthManager,
    json: Json
): CalendarApiClient = CalendarApiClient(httpClient, authManager, json)
```

- [ ] **7.6** Commit : `feat: add CalendarApiClient with 401 refresh retry and 410 sync-token detection`

---

## Task 8 — `PersonMatcher` (email match + stub creation)

- [ ] **8.1** Confirm P1 `PersonDao` exposes :

```kotlin
@Dao
interface PersonDao {
    @Query("SELECT * FROM person WHERE calendar_attendee_id = :email LIMIT 1")
    suspend fun findByCalendarEmail(email: String): PersonEntity?

    @Insert
    suspend fun insert(person: PersonEntity): Long  // or returns Unit for UUID PKs

    // ... other operations from P1 ...
}
```

If missing, add this query in P1 follow-up commit (note flagged in Task 18 deliverables — **do not block P5 on it**, P1 owner adds; but PR includes a P1 amendment if absent).

- [ ] **8.2** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/PersonMatcherTest.kt` :

```kotlin
package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarAttendee
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PersonMatcherTest {

    private val dao = mockk<PersonDao>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
    private val matcher = PersonMatcher(dao, clock)

    @Test
    fun `returns existing person when email already known`() = runTest {
        val existing = PersonEntity(
            id = UUID.randomUUID(),
            name = "Marie Dubois",
            email = "marie@x.com",
            roleHint = null,
            calendarAttendeeId = "marie@x.com",
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            lastInteractionAt = null,
            interactionCount = 0,
            emotionalTrend = null,
            unmatched = false,
            archived = false
        )
        coEvery { dao.findByCalendarEmail("marie@x.com") } returns existing
        val result = matcher.matchOrCreate(
            CalendarAttendee(email = "marie@x.com", displayName = "Marie Dubois")
        )
        assertEquals(existing.id, result?.id)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `creates unmatched stub when no email match (uses displayName)`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns 0L

        val attendee = CalendarAttendee(email = "marc.tremblay@x.com", displayName = "Marc Tremblay")
        val result = matcher.matchOrCreate(attendee)

        assertNotNull(result)
        assertEquals("Marc Tremblay", captured.captured.name)
        assertEquals("marc.tremblay@x.com", captured.captured.email)
        assertEquals("marc.tremblay@x.com", captured.captured.calendarAttendeeId)
        assertTrue(captured.captured.unmatched)
        assertEquals(Instant.parse("2026-05-02T12:00:00Z"), captured.captured.createdAt)
    }

    @Test
    fun `derives name from email when displayName missing`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns 0L

        val attendee = CalendarAttendee(email = "marc.tremblay@x.com", displayName = null)
        matcher.matchOrCreate(attendee)
        assertEquals("Marc Tremblay", captured.captured.name)
    }

    @Test
    fun `derives single-word name from email local-part with no separator`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns 0L
        matcher.matchOrCreate(CalendarAttendee(email = "luc@x.com"))
        assertEquals("Luc", captured.captured.name)
    }

    @Test
    fun `derives name from email with underscore separator`() = runTest {
        coEvery { dao.findByCalendarEmail(any()) } returns null
        val captured = slot<PersonEntity>()
        coEvery { dao.insert(capture(captured)) } returns 0L
        matcher.matchOrCreate(CalendarAttendee(email = "anais_brunet@x.com"))
        assertEquals("Anais Brunet", captured.captured.name)
    }

    @Test
    fun `returns null when attendee has no email and no displayName`() = runTest {
        val result = matcher.matchOrCreate(CalendarAttendee(email = null, displayName = null))
        assertNull(result)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `skips self attendee`() = runTest {
        val result = matcher.matchOrCreate(
            CalendarAttendee(email = "marc@x.com", self = true)
        )
        assertNull(result)
    }

    @Test
    fun `skips resource attendee`() = runTest {
        val result = matcher.matchOrCreate(
            CalendarAttendee(email = "room-1@resource.calendar.google.com", resource = true)
        )
        assertNull(result)
    }
}
```

- [ ] **8.3** Run test. Expect FAIL.

- [ ] **8.4** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/PersonMatcher.kt` :

```kotlin
package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarAttendee
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a calendar event attendee to a `Person` row in the DB. Creates an `unmatched=true`
 * stub if no existing match by email. Skips `self` and `resource` attendees.
 */
@Singleton
class PersonMatcher @Inject constructor(
    private val personDao: PersonDao,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun matchOrCreate(attendee: CalendarAttendee): PersonEntity? {
        if (attendee.self == true || attendee.resource == true) return null
        val email = attendee.email?.takeIf { it.isNotBlank() }
        val displayName = attendee.displayName?.takeIf { it.isNotBlank() }
        if (email == null && displayName == null) return null

        if (email != null) {
            personDao.findByCalendarEmail(email)?.let { return it }
        }

        val resolvedName = displayName ?: email?.let { deriveNameFromEmail(it) } ?: return null
        val stub = PersonEntity(
            id = UUID.randomUUID(),
            name = resolvedName,
            email = email,
            roleHint = null,
            calendarAttendeeId = email,
            createdAt = clock.instant(),
            lastInteractionAt = null,
            interactionCount = 0,
            emotionalTrend = null,
            unmatched = true,
            archived = false
        )
        personDao.insert(stub)
        return stub
    }

    /**
     * "marc.tremblay@x.com" → "Marc Tremblay"
     * "anais_brunet@x.com" → "Anais Brunet"
     * "luc@x.com" → "Luc"
     */
    private fun deriveNameFromEmail(email: String): String {
        val local = email.substringBefore('@')
        val parts = local.split('.', '_', '-').filter { it.isNotEmpty() }
        return parts.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
```

- [ ] **8.5** Run test. Expect PASS.

- [ ] **8.6** Commit : `feat: add PersonMatcher with email-based match and stub creation from attendee`

---

## Task 9 — `CalendarRepository` skeleton + initial sync use-case

- [ ] **9.1** Confirm P1 expose `MeetingDao` and `MeetingAttendeeDao`. Required ops :

```kotlin
@Dao
interface MeetingDao {
    @Query("SELECT * FROM meeting WHERE calendar_event_id = :evtId LIMIT 1")
    suspend fun findByCalendarEventId(evtId: String): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meeting: MeetingEntity)

    @Query("DELETE FROM meeting WHERE calendar_event_id = :evtId")
    suspend fun deleteByCalendarEventId(evtId: String)
}

@Dao
interface MeetingAttendeeDao {
    @Query("DELETE FROM meeting_attendee WHERE meeting_id = :meetingId")
    suspend fun deleteForMeeting(meetingId: UUID)

    @Insert
    suspend fun insertAll(rows: List<MeetingAttendeeEntity>)
}
```

If absent, raise as a P1 amendment in the PR description.

- [ ] **9.2** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/InitialCalendarSyncUseCaseTest.kt` :

```kotlin
package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarAttendee
import com.mamy.android.data.calendar.google.CalendarEvent
import com.mamy.android.data.calendar.google.CalendarEventsList
import com.mamy.android.data.calendar.google.CalendarTime
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.db.entity.PersonEntity
import com.mamy.android.data.settings.CalendarSyncStateStore
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InitialCalendarSyncUseCaseTest {

    private val api = mockk<CalendarApiClient>()
    private val meetingDao = mockk<MeetingDao>(relaxed = true)
    private val attendeeDao = mockk<MeetingAttendeeDao>(relaxed = true)
    private val matcher = mockk<PersonMatcher>()
    private val state = mockk<CalendarSyncStateStore>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
    private val useCase = InitialCalendarSyncUseCase(api, meetingDao, attendeeDao, matcher, state, clock)

    @Test
    fun `syncs single page, persists meeting + attendees, saves syncToken`() = runTest {
        val ev = CalendarEvent(
            id = "evt-1",
            status = "confirmed",
            summary = "1:1 Marie",
            start = CalendarTime(dateTime = "2026-05-02T10:00:00Z"),
            end = CalendarTime(dateTime = "2026-05-02T10:30:00Z"),
            attendees = listOf(
                CalendarAttendee(email = "marie@x.com", displayName = "Marie"),
                CalendarAttendee(email = "marc@x.com", self = true)
            )
        )
        coEvery { api.listEvents("primary", any(), any(), null, null, any()) } returns
            Result.success(CalendarEventsList(items = listOf(ev), nextSyncToken = "sync-1"))
        coEvery { matcher.matchOrCreate(match { it.email == "marie@x.com" }) } returns PersonEntity(
            id = UUID.randomUUID(), name = "Marie", email = "marie@x.com", roleHint = null,
            calendarAttendeeId = "marie@x.com", createdAt = clock.instant(),
            lastInteractionAt = null, interactionCount = 0, emotionalTrend = null,
            unmatched = false, archived = false
        )
        coEvery { matcher.matchOrCreate(match { it.self == true }) } returns null
        coEvery { meetingDao.findByCalendarEventId("evt-1") } returns null

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        coVerify { meetingDao.upsert(match<MeetingEntity> { it.calendarEventId == "evt-1" }) }
        coVerify { attendeeDao.insertAll(match { it.size == 1 }) }
        coVerify { state.saveSyncToken("primary", "sync-1") }
    }

    @Test
    fun `paginates when nextPageToken returned`() = runTest {
        coEvery { api.listEvents("primary", any(), any(), null, null, any()) } returns
            Result.success(CalendarEventsList(items = emptyList(), nextPageToken = "p2"))
        coEvery { api.listEvents("primary", any(), any(), null, "p2", any()) } returns
            Result.success(CalendarEventsList(items = emptyList(), nextSyncToken = "sync-final"))

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        coVerify { state.saveSyncToken("primary", "sync-final") }
    }

    @Test
    fun `cancelled events delete prior meeting`() = runTest {
        val cancelled = CalendarEvent(id = "evt-9", status = "cancelled")
        coEvery { api.listEvents(any(), any(), any(), null, null, any()) } returns
            Result.success(CalendarEventsList(items = listOf(cancelled), nextSyncToken = "s"))
        useCase.execute()
        coVerify { meetingDao.deleteByCalendarEventId("evt-9") }
    }

    @Test
    fun `propagates failure from API`() = runTest {
        coEvery { api.listEvents(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("boom"))
        val res = useCase.execute()
        assertTrue(res.isFailure)
    }
}
```

- [ ] **9.3** Run test. Expect FAIL.

- [ ] **9.4** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/settings/CalendarSyncStateStore.kt` :

```kotlin
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
```

- [ ] **9.5** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/InitialCalendarSyncUseCase.kt` :

```kotlin
package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarEvent
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.settings.CalendarSyncStateStore
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class InitialCalendarSyncUseCase @Inject constructor(
    private val api: CalendarApiClient,
    private val meetingDao: MeetingDao,
    private val attendeeDao: MeetingAttendeeDao,
    private val personMatcher: PersonMatcher,
    private val state: CalendarSyncStateStore,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun execute(
        calendarId: String = "primary",
        pastDays: Long = 30,
        futureDays: Long = 30
    ): Result<Unit> {
        val now = clock.instant()
        val timeMin = now.minusSeconds(pastDays * 86_400)
        val timeMax = now.plusSeconds(futureDays * 86_400)

        var pageToken: String? = null
        var lastSyncToken: String? = null

        do {
            val resp = api.listEvents(
                calendarId = calendarId,
                timeMin = timeMin,
                timeMax = timeMax,
                syncToken = null,
                pageToken = pageToken
            )
            val list = resp.getOrElse { return Result.failure(it) }
            list.items.forEach { applyEvent(it) }
            pageToken = list.nextPageToken
            lastSyncToken = list.nextSyncToken ?: lastSyncToken
        } while (pageToken != null)

        if (lastSyncToken != null) state.saveSyncToken(calendarId, lastSyncToken)
        return Result.success(Unit)
    }

    private suspend fun applyEvent(ev: CalendarEvent) {
        if (ev.status == "cancelled") {
            meetingDao.deleteByCalendarEventId(ev.id)
            return
        }
        val startsAt = parseInstant(ev.start?.dateTime ?: ev.start?.date) ?: return
        val endsAt = parseInstant(ev.end?.dateTime ?: ev.end?.date) ?: startsAt.plusSeconds(1800)

        val existing = meetingDao.findByCalendarEventId(ev.id)
        val meetingId = existing?.id ?: UUID.randomUUID()
        val meeting = MeetingEntity(
            id = meetingId,
            calendarEventId = ev.id,
            title = ev.summary ?: "(untitled)",
            startsAt = startsAt,
            endsAt = endsAt,
            briefingText = existing?.briefingText,
            postNoteId = existing?.postNoteId,
            createdAt = existing?.createdAt ?: clock.instant()
        )
        meetingDao.upsert(meeting)
        attendeeDao.deleteForMeeting(meetingId)

        val attendeeRows = ev.attendees.orEmpty().mapNotNull { att ->
            val person = personMatcher.matchOrCreate(att) ?: return@mapNotNull null
            MeetingAttendeeEntity(meetingId = meetingId, personId = person.id)
        }
        if (attendeeRows.isNotEmpty()) attendeeDao.insertAll(attendeeRows)
    }

    private fun parseInstant(value: String?): Instant? {
        if (value == null) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching {
                // all-day "YYYY-MM-DD" fallback : interpret as local midnight UTC
                Instant.parse(value + "T00:00:00Z")
            }.getOrNull()
    }
}
```

- [ ] **9.6** Run test. Expect PASS.

- [ ] **9.7** Commit : `feat: add InitialCalendarSyncUseCase with pagination and cancel/upsert handling`

---

## Task 10 — `DeltaCalendarSyncUseCase` (incremental via syncToken)

- [ ] **10.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/DeltaCalendarSyncUseCaseTest.kt` :

```kotlin
package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarEventsList
import com.mamy.android.data.calendar.google.CalendarSyncTokenInvalidException
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.settings.CalendarSyncStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeltaCalendarSyncUseCaseTest {

    private val api = mockk<CalendarApiClient>(relaxed = true)
    private val state = mockk<CalendarSyncStateStore>(relaxed = true)
    private val initial = mockk<InitialCalendarSyncUseCase>(relaxed = true)
    private val meetingDao = mockk<MeetingDao>(relaxed = true)
    private val attendeeDao = mockk<MeetingAttendeeDao>(relaxed = true)
    private val matcher = mockk<PersonMatcher>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC)
    private val useCase = DeltaCalendarSyncUseCase(
        api, state, initial, meetingDao, attendeeDao, matcher, clock
    )

    @Test
    fun `delegates to initial sync when no stored sync token`() = runTest {
        every { state.loadSyncToken("primary") } returns null
        coEvery { initial.execute("primary", any(), any()) } returns Result.success(Unit)
        val res = useCase.execute()
        assertTrue(res.isSuccess)
        coVerify { initial.execute("primary", any(), any()) }
        coVerify(exactly = 0) { api.listEvents(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `runs delta with stored sync token, updates token`() = runTest {
        every { state.loadSyncToken("primary") } returns "abc"
        coEvery {
            api.listEvents("primary", null, null, "abc", null, any())
        } returns Result.success(CalendarEventsList(items = emptyList(), nextSyncToken = "def"))
        val res = useCase.execute()
        assertTrue(res.isSuccess)
        coVerify { state.saveSyncToken("primary", "def") }
    }

    @Test
    fun `falls back to initial sync when token returns 410`() = runTest {
        every { state.loadSyncToken("primary") } returns "stale"
        coEvery {
            api.listEvents("primary", null, null, "stale", null, any())
        } returns Result.failure(CalendarSyncTokenInvalidException())
        coEvery { initial.execute("primary", any(), any()) } returns Result.success(Unit)
        val res = useCase.execute()
        assertTrue(res.isSuccess)
        coVerify { state.clearSyncToken("primary") }
        coVerify { initial.execute("primary", any(), any()) }
    }
}
```

- [ ] **10.2** Run test. Expect FAIL.

- [ ] **10.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/DeltaCalendarSyncUseCase.kt` :

```kotlin
package com.mamy.android.data.calendar

import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarSyncTokenInvalidException
import com.mamy.android.data.db.dao.MeetingAttendeeDao
import com.mamy.android.data.db.dao.MeetingDao
import com.mamy.android.data.db.entity.MeetingAttendeeEntity
import com.mamy.android.data.db.entity.MeetingEntity
import com.mamy.android.data.settings.CalendarSyncStateStore
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

class DeltaCalendarSyncUseCase @Inject constructor(
    private val api: CalendarApiClient,
    private val state: CalendarSyncStateStore,
    private val initialSync: InitialCalendarSyncUseCase,
    private val meetingDao: MeetingDao,
    private val attendeeDao: MeetingAttendeeDao,
    private val personMatcher: PersonMatcher,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun execute(calendarId: String = "primary"): Result<Unit> {
        val token = state.loadSyncToken(calendarId)
            ?: return initialSync.execute(calendarId)

        var pageToken: String? = null
        var nextSync: String? = null
        do {
            val resp = api.listEvents(
                calendarId = calendarId,
                timeMin = null,
                timeMax = null,
                syncToken = if (pageToken == null) token else null,
                pageToken = pageToken
            )
            val list = resp.getOrElse { err ->
                if (err is CalendarSyncTokenInvalidException) {
                    state.clearSyncToken(calendarId)
                    return initialSync.execute(calendarId)
                }
                return Result.failure(err)
            }
            list.items.forEach { ev ->
                if (ev.status == "cancelled") {
                    meetingDao.deleteByCalendarEventId(ev.id)
                    return@forEach
                }
                val startsAt = ev.start?.dateTime?.let { OffsetDateTime.parse(it).toInstant() } ?: return@forEach
                val endsAt = ev.end?.dateTime?.let { OffsetDateTime.parse(it).toInstant() } ?: startsAt.plusSeconds(1800)
                val existing = meetingDao.findByCalendarEventId(ev.id)
                val mid = existing?.id ?: UUID.randomUUID()
                meetingDao.upsert(
                    MeetingEntity(
                        id = mid,
                        calendarEventId = ev.id,
                        title = ev.summary ?: "(untitled)",
                        startsAt = startsAt,
                        endsAt = endsAt,
                        briefingText = existing?.briefingText,
                        postNoteId = existing?.postNoteId,
                        createdAt = existing?.createdAt ?: clock.instant()
                    )
                )
                attendeeDao.deleteForMeeting(mid)
                val rows = ev.attendees.orEmpty().mapNotNull { att ->
                    val p = personMatcher.matchOrCreate(att) ?: return@mapNotNull null
                    MeetingAttendeeEntity(meetingId = mid, personId = p.id)
                }
                if (rows.isNotEmpty()) attendeeDao.insertAll(rows)
            }
            pageToken = list.nextPageToken
            nextSync = list.nextSyncToken ?: nextSync
        } while (pageToken != null)

        if (nextSync != null) state.saveSyncToken(calendarId, nextSync)
        return Result.success(Unit)
    }
}
```

- [ ] **10.4** Run test. Expect PASS.

- [ ] **10.5** Commit : `feat: add DeltaCalendarSyncUseCase with 410 fallback to initial sync`

---

## Task 11 — `CalendarSyncWorker` (WorkManager)

- [ ] **11.1** Add to `app/build.gradle.kts` if not already present from P1 :

```kotlin
implementation(libs.work)
implementation(libs.hilt.work)
ksp(libs.hilt.compiler.work)

androidTestImplementation(libs.work.testing)
testImplementation(libs.work.testing)
```

Add `work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }` to `libs.versions.toml`.

- [ ] **11.2** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/CalendarSyncWorkerTest.kt` :

```kotlin
package com.mamy.android.data.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CalendarSyncWorkerTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `success when delta sync succeeds`() = runTest {
        val delta = mockk<DeltaCalendarSyncUseCase>()
        coEvery { delta.execute("primary") } returns kotlin.Result.success(Unit)

        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(ctx)
            .setWorkerFactory(TestCalendarSyncWorkerFactory(delta))
            .build()

        val result = worker.startWork().get()
        assertEquals(Result.success(), result)
    }

    @Test
    fun `retry when delta sync fails with transient error`() = runTest {
        val delta = mockk<DeltaCalendarSyncUseCase>()
        coEvery { delta.execute(any()) } returns kotlin.Result.failure(RuntimeException("network"))

        val worker = TestListenableWorkerBuilder<CalendarSyncWorker>(ctx)
            .setWorkerFactory(TestCalendarSyncWorkerFactory(delta))
            .build()

        val result = worker.startWork().get()
        assertEquals(Result.retry(), result)
    }
}

private class TestCalendarSyncWorkerFactory(
    private val delta: DeltaCalendarSyncUseCase
) : androidx.work.WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): androidx.work.ListenableWorker = CalendarSyncWorker(appContext, workerParameters, delta)
}
```

- [ ] **11.3** Run test. Expect FAIL.

- [ ] **11.4** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/CalendarSyncWorker.kt` :

```kotlin
package com.mamy.android.data.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deltaSync: DeltaCalendarSyncUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val calendarId = inputData.getString(KEY_CALENDAR_ID) ?: "primary"
        return deltaSync.execute(calendarId).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val KEY_CALENDAR_ID = "calendar_id"
        const val UNIQUE_NAME = "mamy_calendar_periodic"
    }
}
```

- [ ] **11.5** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/CalendarSyncScheduler.kt` :

```kotlin
package com.mamy.android.data.calendar

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CalendarSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(CalendarSyncWorker.UNIQUE_NAME)
    }
}
```

- [ ] **11.6** Run test. Expect PASS.

- [ ] **11.7** Commit : `feat: add CalendarSyncWorker (15-min periodic) + Scheduler + Hilt wiring`

---

## Task 12 — Settings flag `calendar_enabled` + conditional scheduling

- [ ] **12.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/settings/CalendarSettingsTest.kt` :

```kotlin
package com.mamy.android.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
```

- [ ] **12.2** Run test. Expect FAIL.

- [ ] **12.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/settings/CalendarSettings.kt` :

```kotlin
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
```

- [ ] **12.4** Run test. Expect PASS.

- [ ] **12.5** Wire conditional scheduling in `MamYApplication.onCreate` (existing P1 file) :

```kotlin
// inside MamYApplication
@Inject lateinit var calendarSettings: CalendarSettings
@Inject lateinit var calendarSyncScheduler: CalendarSyncScheduler

override fun onCreate() {
    super.onCreate()
    // ... existing init ...
    applicationScope.launch {
        calendarSettings.isCalendarEnabled.collect { enabled ->
            if (enabled) calendarSyncScheduler.schedulePeriodic()
            else calendarSyncScheduler.cancelPeriodic()
        }
    }
}
```

(Use whichever `applicationScope` P1 already exposes; if absent, declare `private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` in `MamYApplication`.)

- [ ] **12.6** Commit : `feat: add CalendarSettings flag and conditional WorkManager scheduling`

---

## Task 13 — `ConfirmPersonStubUseCase` (logic for P7 UI)

- [ ] **13.1** Add DAO ops needed (P1 amendment if absent) :

```kotlin
@Query("SELECT * FROM person WHERE unmatched = 1 AND archived = 0 ORDER BY created_at DESC")
fun observeUnmatched(): Flow<List<PersonEntity>>

@Update suspend fun update(person: PersonEntity)

@Query("DELETE FROM person WHERE id = :id")
suspend fun deleteById(id: UUID)
```

- [ ] **13.2** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/domain/memory/ConfirmPersonStubUseCaseTest.kt` :

```kotlin
package com.mamy.android.domain.memory

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ConfirmPersonStubUseCaseTest {

    private val dao = mockk<PersonDao>(relaxed = true)
    private val useCase = ConfirmPersonStubUseCase(dao)

    private fun stub(name: String = "Marc Tremblay") = PersonEntity(
        id = UUID.randomUUID(),
        name = name,
        email = "marc@x.com",
        roleHint = null,
        calendarAttendeeId = "marc@x.com",
        createdAt = Instant.parse("2026-05-02T12:00:00Z"),
        lastInteractionAt = null,
        interactionCount = 0,
        emotionalTrend = null,
        unmatched = true,
        archived = false
    )

    @Test
    fun `confirm flips unmatched flag to false`() = runTest {
        val s = stub()
        val captured = slot<PersonEntity>()
        coEvery { dao.findById(s.id) } returns s
        coEvery { dao.update(capture(captured)) } returns Unit

        useCase.confirm(s.id)

        assertEquals(false, captured.captured.unmatched)
        assertEquals(s.id, captured.captured.id)
    }

    @Test
    fun `editName updates name and confirms`() = runTest {
        val s = stub()
        val captured = slot<PersonEntity>()
        coEvery { dao.findById(s.id) } returns s
        coEvery { dao.update(capture(captured)) } returns Unit

        useCase.editName(s.id, "Marc-André Tremblay")

        assertEquals("Marc-André Tremblay", captured.captured.name)
        assertEquals(false, captured.captured.unmatched)
    }

    @Test
    fun `mergeInto archives stub and re-attaches attendee id to target`() = runTest {
        val stubPerson = stub()
        val target = stub(name = "Marc Already").copy(unmatched = false, calendarAttendeeId = null)
        coEvery { dao.findById(stubPerson.id) } returns stubPerson
        coEvery { dao.findById(target.id) } returns target
        val captures = mutableListOf<PersonEntity>()
        coEvery { dao.update(capture(captures)) } returns Unit

        useCase.mergeInto(stubId = stubPerson.id, targetId = target.id)

        // Target gets the attendee id
        val targetUpdate = captures.first { it.id == target.id }
        assertEquals("marc@x.com", targetUpdate.calendarAttendeeId)
        // Stub gets archived
        val stubUpdate = captures.first { it.id == stubPerson.id }
        assertEquals(true, stubUpdate.archived)
    }
}
```

- [ ] **13.3** Run test. Expect FAIL.

- [ ] **13.4** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/memory/ConfirmPersonStubUseCase.kt` :

```kotlin
package com.mamy.android.domain.memory

import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class ConfirmPersonStubUseCase @Inject constructor(
    private val personDao: PersonDao
) {

    fun observeUnmatched(): Flow<List<PersonEntity>> = personDao.observeUnmatched()

    suspend fun confirm(personId: UUID) {
        val current = personDao.findById(personId) ?: return
        personDao.update(current.copy(unmatched = false))
    }

    suspend fun editName(personId: UUID, newName: String) {
        val current = personDao.findById(personId) ?: return
        personDao.update(current.copy(name = newName, unmatched = false))
    }

    /**
     * Merges [stubId] into [targetId]: target inherits the calendar_attendee_id, stub is archived.
     * Future calendar events with that attendee email will resolve to the target person.
     * NOTE: meeting_attendee rows pointing at stubId are NOT rewritten in V1 — they stay historical.
     * V2 may add an attendee-rewrite migration use-case.
     */
    suspend fun mergeInto(stubId: UUID, targetId: UUID) {
        val stub = personDao.findById(stubId) ?: return
        val target = personDao.findById(targetId) ?: return
        if (stub.calendarAttendeeId != null) {
            personDao.update(target.copy(calendarAttendeeId = stub.calendarAttendeeId))
        }
        personDao.update(stub.copy(archived = true, unmatched = false))
    }
}
```

- [ ] **13.5** Run test. Expect PASS.

- [ ] **13.6** Commit : `feat: add ConfirmPersonStubUseCase (confirm/edit/merge logic for unmatched stubs)`

---

## Task 14 — `CalendarOnboardingUseCase` (sign-in → initial sync chain)

- [ ] **14.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/domain/calendar/CalendarOnboardingUseCaseTest.kt` :

```kotlin
package com.mamy.android.domain.calendar

import com.mamy.android.data.calendar.InitialCalendarSyncUseCase
import com.mamy.android.data.calendar.CalendarSyncScheduler
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalendarOnboardingUseCaseTest {

    private val auth = mockk<CalendarAuthManager>(relaxed = true)
    private val initial = mockk<InitialCalendarSyncUseCase>(relaxed = true)
    private val scheduler = mockk<CalendarSyncScheduler>(relaxed = true)
    private val settings = mockk<CalendarSettings>(relaxed = true)
    private val useCase = CalendarOnboardingUseCase(auth, initial, scheduler, settings)

    @Test
    fun `completeOnboarding chains: persist tokens, initial sync, schedule, set flag`() = runTest {
        coEvery { auth.completeAuthorization("code-1", "marc@x.com", any()) } returns mockk(relaxed = true)
        coEvery { initial.execute("primary", any(), any()) } returns Result.success(Unit)

        val result = useCase.completeOnboarding(
            authCode = "code-1",
            accountEmail = "marc@x.com",
            scope = "https://www.googleapis.com/auth/calendar.readonly"
        )

        assertTrue(result.isSuccess)
        coVerify { initial.execute("primary", any(), any()) }
        coVerify { scheduler.schedulePeriodic() }
        coVerify { settings.setCalendarEnabled(true) }
    }

    @Test
    fun `disconnect clears auth, cancels scheduler, sets flag false`() = runTest {
        useCase.disconnect()
        coVerify { auth.signOut() }
        coVerify { scheduler.cancelPeriodic() }
        coVerify { settings.setCalendarEnabled(false) }
    }

    @Test
    fun `completeOnboarding returns failure if auth exchange fails`() = runTest {
        coEvery { auth.completeAuthorization(any(), any(), any()) } returns null
        val result = useCase.completeOnboarding("bad", "e@x.com", "s")
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { initial.execute(any(), any(), any()) }
    }
}
```

- [ ] **14.2** Run test. Expect FAIL.

- [ ] **14.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/domain/calendar/CalendarOnboardingUseCase.kt` :

```kotlin
package com.mamy.android.domain.calendar

import com.mamy.android.data.calendar.CalendarSyncScheduler
import com.mamy.android.data.calendar.InitialCalendarSyncUseCase
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import javax.inject.Inject

class CalendarOnboardingUseCase @Inject constructor(
    private val authManager: CalendarAuthManager,
    private val initialSync: InitialCalendarSyncUseCase,
    private val scheduler: CalendarSyncScheduler,
    private val settings: CalendarSettings
) {

    suspend fun completeOnboarding(
        authCode: String,
        accountEmail: String,
        scope: String
    ): Result<Unit> {
        val tokens = authManager.completeAuthorization(authCode, accountEmail, scope)
            ?: return Result.failure(IllegalStateException("authorization exchange failed"))
        val sync = initialSync.execute()
        if (sync.isFailure) return sync
        scheduler.schedulePeriodic()
        settings.setCalendarEnabled(true)
        return Result.success(Unit)
    }

    suspend fun disconnect() {
        scheduler.cancelPeriodic()
        authManager.signOut()
        settings.setCalendarEnabled(false)
    }
}
```

- [ ] **14.4** Run test. Expect PASS.

- [ ] **14.5** Commit : `feat: add CalendarOnboardingUseCase chaining auth, initial sync, scheduler, flag`

---

## Task 15 — `CalendarSettingsViewModel` (settings UI hook for P7)

- [ ] **15.1** Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/ui/screens/settings/CalendarSettingsViewModelTest.kt` :

```kotlin
package com.mamy.android.ui.screens.settings

import app.cash.turbine.test
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.domain.calendar.CalendarOnboardingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarSettingsViewModelTest {

    private val onboarding = mockk<CalendarOnboardingUseCase>(relaxed = true)
    private val auth = mockk<CalendarAuthManager>(relaxed = true)
    private val settings = mockk<CalendarSettings>(relaxed = true)
    private val enabledFlow = MutableStateFlow(false)

    @BeforeEach
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { settings.isCalendarEnabled } returns enabledFlow
    }

    @Test
    fun `state reflects calendar_enabled flag`() = runTest {
        val vm = CalendarSettingsViewModel(onboarding, auth, settings)
        vm.uiState.test {
            assertEquals(false, awaitItem().calendarEnabled)
            enabledFlow.value = true
            assertEquals(true, awaitItem().calendarEnabled)
        }
    }

    @Test
    fun `onAuthCodeReceived calls onboarding`() = runTest {
        coEvery { onboarding.completeOnboarding(any(), any(), any()) } returns Result.success(Unit)
        val vm = CalendarSettingsViewModel(onboarding, auth, settings)
        vm.onAuthCodeReceived("c", "e@x.com", "s")
        coVerify { onboarding.completeOnboarding("c", "e@x.com", "s") }
    }

    @Test
    fun `onDisconnect calls onboarding disconnect`() = runTest {
        val vm = CalendarSettingsViewModel(onboarding, auth, settings)
        vm.onDisconnect()
        coVerify { onboarding.disconnect() }
    }
}
```

- [ ] **15.2** Run test. Expect FAIL.

- [ ] **15.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/ui/screens/settings/CalendarSettingsViewModel.kt` :

```kotlin
package com.mamy.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.settings.CalendarSettings
import com.mamy.android.domain.calendar.CalendarOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarSettingsUiState(
    val calendarEnabled: Boolean = false,
    val syncing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val onboarding: CalendarOnboardingUseCase,
    private val authManager: CalendarAuthManager,
    private val settings: CalendarSettings
) : ViewModel() {

    val uiState: StateFlow<CalendarSettingsUiState> =
        settings.isCalendarEnabled
            .map { CalendarSettingsUiState(calendarEnabled = it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSettingsUiState())

    fun onAuthCodeReceived(authCode: String, accountEmail: String, scope: String) {
        viewModelScope.launch {
            val result = onboarding.completeOnboarding(authCode, accountEmail, scope)
            // Error surfacing: map result to a side-channel SharedFlow in V1.1; for V1, log.
            result.exceptionOrNull()?.printStackTrace()
        }
    }

    fun onDisconnect() {
        viewModelScope.launch { onboarding.disconnect() }
    }
}
```

- [ ] **15.4** Run test. Expect PASS.

- [ ] **15.5** Commit : `feat: add CalendarSettingsViewModel exposing calendar_enabled state and connect/disconnect`

---

## Task 16 — `NetworkLogScreen` hook : log calendar OAuth + API calls

- [ ] **16.1** Add a category enum entry for calendar in P1's `NetworkLogEntry` (assume P1 owns the model). Write failing test `D:/ComfyUI-Intel/mamy/app/src/test/kotlin/com/mamy/android/data/calendar/google/CalendarHttpLoggingTest.kt` :

```kotlin
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
```

- [ ] **16.2** Run test. Expect FAIL.

- [ ] **16.3** Implement `D:/ComfyUI-Intel/mamy/app/src/main/kotlin/com/mamy/android/data/calendar/google/CalendarHttpLogger.kt` :

```kotlin
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
```

If P1's `NetworkLogEntry` does not yet have a `CALENDAR` category, raise as a P1 amendment in PR description.

- [ ] **16.4** Update `CalendarModule` to install the logger on `calendar_http_raw` :

```kotlin
@Provides @Singleton @Named("calendar_http_raw")
fun provideRawCalendarHttp(logger: CalendarHttpLogger): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor(logger)
    .build()
```

- [ ] **16.5** Run test. Expect PASS.

- [ ] **16.6** Commit : `feat: log Google Calendar HTTP calls into NetworkLogStore (CALENDAR category)`

---

## Task 17 — i18n strings + manifest permissions

- [ ] **17.1** Add to `D:/ComfyUI-Intel/mamy/app/src/main/res/values/strings.xml` :

```xml
<string name="settings_calendar_section">Calendar</string>
<string name="settings_calendar_connect_google">Connect Google Calendar</string>
<string name="settings_calendar_disconnect">Disconnect</string>
<string name="settings_calendar_status_connected">Connected as %1$s</string>
<string name="settings_calendar_status_disconnected">Not connected</string>
<string name="settings_calendar_disabled_hint">Briefings will only run on demand. Person matching from events is disabled.</string>
<string name="onboarding_unmatched_person_prompt">New person: %1$s. Confirm?</string>
<string name="onboarding_unmatched_btn_confirm">Confirm</string>
<string name="onboarding_unmatched_btn_edit">Edit</string>
<string name="onboarding_unmatched_btn_merge">Merge with…</string>
```

- [ ] **17.2** Add to `D:/ComfyUI-Intel/mamy/app/src/main/res/values-fr/strings.xml` :

```xml
<string name="settings_calendar_section">Calendrier</string>
<string name="settings_calendar_connect_google">Connecter Google Calendar</string>
<string name="settings_calendar_disconnect">Déconnecter</string>
<string name="settings_calendar_status_connected">Connecté en tant que %1$s</string>
<string name="settings_calendar_status_disconnected">Non connecté</string>
<string name="settings_calendar_disabled_hint">Les briefings ne s\'exécuteront que sur demande. La correspondance par calendrier est désactivée.</string>
<string name="onboarding_unmatched_person_prompt">Nouvelle personne : %1$s. Tu confirmes ?</string>
<string name="onboarding_unmatched_btn_confirm">Confirmer</string>
<string name="onboarding_unmatched_btn_edit">Éditer</string>
<string name="onboarding_unmatched_btn_merge">Fusionner avec…</string>
```

- [ ] **17.3** Add to `D:/ComfyUI-Intel/mamy/app/src/main/AndroidManifest.xml` (above `<application>` if not already there) :

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

- [ ] **17.4** Commit : `feat: add P5 i18n strings (FR+EN) and INTERNET permission`

---

## Task 18 — End-to-end smoke test (instrumented, opt-in)

- [ ] **18.1** Write `D:/ComfyUI-Intel/mamy/app/src/androidTest/kotlin/com/mamy/android/data/calendar/CalendarSyncSmokeTest.kt` :

```kotlin
package com.mamy.android.data.calendar

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.db.MamYDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Smoke test that exercises the full P5 chain against a REAL Google account.
 * Skipped unless env var `MAMY_GCAL_SMOKE=1` is set on the test device.
 *
 * Manual prep (operator):
 *   1. Sign in to a dogfood Google account on the emulator.
 *   2. Ensure the account has at least 1 future calendar event in the next 30 days.
 *   3. Run with: ./gradlew connectedDebugAndroidTest -PandroidTestInstrumentation.MAMY_GCAL_SMOKE=1
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarSyncSmokeTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var initial: InitialCalendarSyncUseCase
    @Inject lateinit var auth: CalendarAuthManager
    @Inject lateinit var db: MamYDatabase

    @Before fun setup() {
        hiltRule.inject()
        assumeTrue(System.getenv("MAMY_GCAL_SMOKE") == "1")
    }

    @Test fun fullSync_populatesMeetingsAndPersons() = runBlocking {
        assumeTrue(auth.isSignedIn())
        val result = initial.execute()
        assert(result.isSuccess) { "sync failed: ${result.exceptionOrNull()}" }
        val meetings = db.meetingDao().countAll()
        assert(meetings > 0) { "expected at least 1 meeting in DB after sync, got $meetings" }
        val persons = db.personDao().countAll()
        assert(persons > 0) { "expected at least 1 person row, got $persons" }
    }
}
```

- [ ] **18.2** Add to `MeetingDao` and `PersonDao` (P1 amendment if absent) :

```kotlin
@Query("SELECT COUNT(*) FROM meeting") suspend fun countAll(): Int
@Query("SELECT COUNT(*) FROM person")  suspend fun countAll(): Int
```

- [ ] **18.3** Document the manual run procedure in `D:/ComfyUI-Intel/mamy/docs/setup/p5-smoke-test.md` :

```markdown
# P5 Calendar smoke test

Prerequisites :
- Pixel emulator (or real device) with Google Play Services
- Dogfood Google account signed in
- At least one future event in the calendar
- Real OAuth client ID written into `calendar_config.xml`

Run :

```bash
adb shell setprop debug.MAMY_GCAL_SMOKE 1
./gradlew connectedDebugAndroidTest --tests "com.mamy.android.data.calendar.CalendarSyncSmokeTest"
```

Expected : PASS, with at least one Meeting + Person row inserted.
```

- [ ] **18.4** Run unit tests + lint : `./gradlew test lint`. Expect clean.

- [ ] **18.5** Commit : `test: add CalendarSyncSmokeTest end-to-end (opt-in via MAMY_GCAL_SMOKE=1) + runbook`

---

## Done criteria

- [ ] All 18 tasks checked.
- [ ] `./gradlew test` green for `data.calendar.*`, `data.calendar.google.*`, `domain.calendar.*`, `domain.memory.ConfirmPersonStubUseCaseTest`, `ui.screens.settings.CalendarSettingsViewModelTest`.
- [ ] `./gradlew lint` reports no new errors.
- [ ] Manual emulator run : Settings → "Connect Google Calendar" → OAuth flow → DB shows Meeting + Person rows (some `unmatched=true`).
- [ ] Disconnect button cancels the WorkManager job (verify with `adb shell dumpsys jobscheduler | grep mamy`).
- [ ] Toggle `calendar_enabled = false` in DataStore → app still launches, manual capture still works, no calendar-driven WorkManager fires.

## P1 amendments listed in PR

If any DAO query / entity column is missing from the P1 baseline, the PR description must call them out explicitly :

- `PersonDao.findByCalendarEmail(email)` — required by Task 8.
- `PersonDao.findById(id)`, `update(person)`, `observeUnmatched()`, `deleteById(id)` — required by Task 13.
- `PersonDao.countAll()`, `MeetingDao.countAll()` — required by Task 18.
- `MeetingDao.findByCalendarEventId`, `upsert`, `deleteByCalendarEventId` — required by Task 9.
- `MeetingAttendeeDao.deleteForMeeting`, `insertAll` — required by Task 9.
- `NetworkLogEntry.Category.CALENDAR` enum value — required by Task 16.

## Hand-off to P6

After P5 ships, the `Briefing` table is empty but `Meeting` + `MeetingAttendee` + `Person` are populated. P6 (briefing generation) reads these to assemble LLM context. P5 deliberately stops at "calendar data in DB" — no briefing logic.

## Hand-off to P7 (UI)

P7 implements :
- The "Connect Google Calendar" button → triggers `CalendarSettingsViewModel.onAuthCodeReceived` (uses `androidx.credentials` UI flow).
- The unmatched-person confirmation prompt → uses `ConfirmPersonStubUseCase.observeUnmatched()` + `confirm/editName/mergeInto`.
- The settings flag toggle UI → `CalendarSettingsViewModel.uiState.calendarEnabled`.
