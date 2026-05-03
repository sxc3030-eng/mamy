package com.mamy.android.di

import com.mamy.android.data.tts.TextToSpeechAdapter
import com.mamy.android.data.tts.TextToSpeechAdapterImpl
import com.mamy.android.domain.intent.handler.DailyBriefHandler
import com.mamy.android.domain.intent.handler.EodSummaryHandler
import com.mamy.android.domain.intent.handler.NextBriefHandler
import com.mamy.android.domain.intent.handler.PersonBriefHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds briefing handler interfaces to their V1 impls.
 * P6 will provide a `LlmBriefingModule` that overrides these via `@TestInstallIn`
 * (replace strategy) to swap the stubs for LLM-backed handlers.
 *
 * Also binds the [TextToSpeechAdapter] used by [com.mamy.android.domain.intent.handler.HomonymeClarifier].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntentModule {

    @Binds
    @Singleton
    abstract fun bindDailyBriefHandler(impl: com.mamy.android.domain.briefing.DailyBriefHandler): DailyBriefHandler

    @Binds
    @Singleton
    abstract fun bindNextBriefHandler(impl: com.mamy.android.domain.briefing.PreMeetingBriefHandler): NextBriefHandler

    @Binds
    @Singleton
    abstract fun bindEodSummaryHandler(impl: com.mamy.android.domain.briefing.EodSummaryHandler): EodSummaryHandler

    @Binds
    @Singleton
    abstract fun bindPersonBriefHandler(impl: com.mamy.android.domain.briefing.PersonQueryBriefHandler): PersonBriefHandler

    @Binds
    @Singleton
    abstract fun bindTextToSpeechAdapter(impl: TextToSpeechAdapterImpl): TextToSpeechAdapter
}
