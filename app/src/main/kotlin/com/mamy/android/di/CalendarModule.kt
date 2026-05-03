package com.mamy.android.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mamy.android.data.calendar.google.CalendarApiClient
import com.mamy.android.data.calendar.google.CalendarAuthManager
import com.mamy.android.data.calendar.google.CalendarHttpLogger
import com.mamy.android.data.calendar.google.CalendarTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

    @Provides @Singleton @Named("calendar_http_raw")
    fun provideRawCalendarHttp(logger: CalendarHttpLogger): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logger)
        .build()

    @Provides @Singleton
    fun provideCalendarAuthManager(
        @ApplicationContext context: Context,
        tokenStore: CalendarTokenStore,
        @Named("calendar_http_raw") httpClient: OkHttpClient
    ): CalendarAuthManager {
        val resId = context.resources.getIdentifier(
            "google_oauth_web_client_id", "string", context.packageName
        )
        val webClientId = if (resId != 0) context.getString(resId) else ""
        return CalendarAuthManager(
            context = context,
            tokenStore = tokenStore,
            httpClient = httpClient,
            webClientId = webClientId
        )
    }

    @Provides @Singleton
    fun provideCalendarApiClient(
        @Named("calendar_http_raw") httpClient: OkHttpClient,
        authManager: CalendarAuthManager,
        json: Json
    ): CalendarApiClient = CalendarApiClient(httpClient, authManager, json)
}
