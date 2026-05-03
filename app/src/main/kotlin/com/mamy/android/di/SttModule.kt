package com.mamy.android.di

import android.content.Context
import com.mamy.android.data.stt.WhisperEngine
import com.mamy.android.data.stt.WhisperEngineImpl
import com.mamy.android.data.stt.WhisperModel
import com.mamy.android.data.stt.WhisperModelDownloader
import com.mamy.android.data.stt.jni.WhisperJni
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SttModule {

    @Provides @Singleton
    fun provideWhisperJni(): WhisperJni = WhisperJni()

    @Provides @Singleton
    fun provideWhisperModel(): WhisperModel = WhisperModel.TINY

    @Provides @Singleton
    fun provideDownloader(@ApplicationContext ctx: Context): WhisperModelDownloader =
        WhisperModelDownloader(File(ctx.filesDir, "whisper"))

    @Provides @Singleton
    fun provideWhisperEngine(impl: WhisperEngineImpl): WhisperEngine = impl
}
