package com.mamy.android.di

import android.content.Context
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.data.wakeword.PorcupineWakeWordEngine
import com.mamy.android.data.wakeword.WakeWordEngine
import com.mamy.android.data.wakeword.WakeWordModelResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WakeWordModule {

    private const val PICOVOICE_ACCESS_KEY = "picovoice_access_key"

    @Provides @Singleton
    fun provideModelResolver(@ApplicationContext ctx: Context): WakeWordModelResolver =
        WakeWordModelResolver(ctx)

    @Provides @Singleton
    fun provideWakeWordEngine(
        @ApplicationContext ctx: Context,
        resolver: WakeWordModelResolver,
        secrets: SecretsVault,
    ): WakeWordEngine = PorcupineWakeWordEngine(
        context = ctx,
        resolver = resolver,
        accessKeyProvider = { secrets.getSecret(PICOVOICE_ACCESS_KEY).orEmpty() },
        localeProvider = { Locale.getDefault() },
    )
}
