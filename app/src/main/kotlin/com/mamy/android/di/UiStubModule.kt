package com.mamy.android.di

import com.mamy.android.ui.screens.data.DataActions
import com.mamy.android.ui.screens.data.DataStatsSource
import com.mamy.android.ui.screens.data.NoOpDataActions
import com.mamy.android.ui.screens.data.NoOpDataStatsSource
import com.mamy.android.ui.screens.data.NoOpSmsHistorySource
import com.mamy.android.ui.screens.data.SmsHistorySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for UI-layer stubs that other waves replace later.
 *
 * - [SmsHistorySource]    → W1-E swaps for DAO-backed impl
 * - [DataStatsSource]     → P8 swaps for DAO-aggregated impl
 * - [DataActions]         → P8 swaps for real export/wipe impl
 *
 * Doing this with @Binds keeps the swap a one-line change in a downstream module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UiStubModule {

    @Binds
    @Singleton
    abstract fun bindSmsHistorySource(impl: NoOpSmsHistorySource): SmsHistorySource

    @Binds
    @Singleton
    abstract fun bindDataStatsSource(impl: NoOpDataStatsSource): DataStatsSource

    @Binds
    @Singleton
    abstract fun bindDataActions(impl: NoOpDataActions): DataActions
}
