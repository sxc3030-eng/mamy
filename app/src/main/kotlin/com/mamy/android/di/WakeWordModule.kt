package com.mamy.android.di

import android.content.Context
import com.mamy.android.BuildConfig
import com.mamy.android.data.secrets.SecretsVault
import com.mamy.android.data.wakeword.PorcupineWakeWordEngine
import com.mamy.android.data.wakeword.WakeWordEngine
import com.mamy.android.data.wakeword.WakeWordModelResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WakeWordModule {

    private const val PICOVOICE_ACCESS_KEY = "picovoice_access_key"

    /**
     * Kept for V1.0 when we resume custom `.ppn` distribution (per-locale MamY).
     * V1.5 alpha uses [ai.picovoice.porcupine.Porcupine.BuiltInKeyword.JARVIS] instead.
     */
    @Provides @Singleton
    fun provideModelResolver(@ApplicationContext ctx: Context): WakeWordModelResolver =
        WakeWordModelResolver(ctx)

    @Provides @Singleton
    fun provideWakeWordEngine(
        @ApplicationContext ctx: Context,
        secrets: SecretsVault,
    ): WakeWordEngine = PorcupineWakeWordEngine(
        context = ctx,
        accessKeyProvider = {
            // Prefer user-provided key from SecretsVault (BYOK power user); fall back
            // to the build-time alpha key baked into BuildConfig so testers can run
            // the APK without any setup.
            secrets.getSecret(PICOVOICE_ACCESS_KEY)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.PICOVOICE_ACCESS_KEY
        },
    )
}
