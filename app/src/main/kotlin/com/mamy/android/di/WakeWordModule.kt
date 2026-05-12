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

    @Provides @Singleton
    fun provideModelResolver(@ApplicationContext ctx: Context): WakeWordModelResolver =
        WakeWordModelResolver(ctx)

    /**
     * The engine resolves its keyword at start() time:
     * - custom `mamy_<lang>.ppn` under `assets/wakeword/` if present
     *   (Mamy responds to "MamY"),
     * - else [ai.picovoice.porcupine.Porcupine.BuiltInKeyword.JARVIS]
     *   (V1.5 alpha out-of-the-box, testers say "Jarvis").
     */
    @Provides @Singleton
    fun provideWakeWordEngine(
        @ApplicationContext ctx: Context,
        resolver: WakeWordModelResolver,
        secrets: SecretsVault,
    ): WakeWordEngine = PorcupineWakeWordEngine(
        context = ctx,
        resolver = resolver,
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
