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
