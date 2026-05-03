package com.mamy.android.di

import com.mamy.android.data.llm.LlmProvider
import com.mamy.android.data.llm.LlmProviderFactory
import com.mamy.android.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Hilt providers for the P6 briefing pipeline.
 *
 * Provides the [LlmProvider] used by [com.mamy.android.domain.briefing.BriefingGenerator]
 * by resolving the user-selected provider via [LlmProviderFactory] +
 * [SettingsRepository.selectedLlmProviderFlow]. We block once at injection time
 * to read the current setting; the briefing pipeline doesn't need hot reloads
 * since each generate() call invokes the resolved provider directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object BriefingModule {

    @Provides
    @Singleton
    fun provideBriefingLlmProvider(
        factory: LlmProviderFactory,
        settings: SettingsRepository,
    ): LlmProvider = runBlocking {
        val selected = settings.selectedLlmProviderFlow.first()
        factory.byId(selected.name.lowercase())
    }
}
