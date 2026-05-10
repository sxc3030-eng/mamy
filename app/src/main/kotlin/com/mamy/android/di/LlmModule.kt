package com.mamy.android.di

import com.mamy.android.BuildConfig
import com.mamy.android.data.db.MamYDatabase
import com.mamy.android.data.llm.cost.LlmCostDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Marker for the Ollama backend base URL injected from BuildConfig. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OllamaBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    fun provideLlmCostDao(db: MamYDatabase): LlmCostDao = db.llmCostDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideTtsMessageBuilder(): com.mamy.android.data.tts.TtsConfirmer.MessageBuilder =
        com.mamy.android.data.tts.TtsConfirmer.MessageBuilder()

    @Provides
    @OllamaBaseUrl
    fun provideOllamaBaseUrl(): String = BuildConfig.OLLAMA_BASE_URL
}
