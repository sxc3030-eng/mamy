package com.mamy.android.di

import com.mamy.android.data.audio.AudioCapture
import com.mamy.android.data.audio.AudioCaptureImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds @Singleton
    abstract fun bindAudioCapture(impl: AudioCaptureImpl): AudioCapture
}
