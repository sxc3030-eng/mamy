package com.mamy.android.di

import com.mamy.android.data.tts.TextToSpeechAdapter
import com.mamy.android.data.tts.TextToSpeechAdapterImpl
import com.mamy.android.domain.intent.handler.DailyBriefHandler
import com.mamy.android.domain.intent.handler.EodSummaryHandler
import com.mamy.android.domain.intent.handler.NextBriefHandler
import com.mamy.android.domain.intent.handler.PersonBriefHandler
import com.mamy.android.domain.intent.handler.StubDailyBriefHandler
import com.mamy.android.domain.intent.handler.StubEodSummaryHandler
import com.mamy.android.domain.intent.handler.StubNextBriefHandler
import com.mamy.android.domain.intent.handler.TemplatedPersonBriefHandler
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
    abstract fun bindDailyBriefHandler(impl: StubDailyBriefHandler): DailyBriefHandler

    @Binds
    @Singleton
    abstract fun bindNextBriefHandler(impl: StubNextBriefHandler): NextBriefHandler

    @Binds
    @Singleton
    abstract fun bindEodSummaryHandler(impl: StubEodSummaryHandler): EodSummaryHandler

    @Binds
    @Singleton
    abstract fun bindPersonBriefHandler(impl: TemplatedPersonBriefHandler): PersonBriefHandler

    @Binds
    @Singleton
    abstract fun bindTextToSpeechAdapter(impl: TextToSpeechAdapterImpl): TextToSpeechAdapter
}
