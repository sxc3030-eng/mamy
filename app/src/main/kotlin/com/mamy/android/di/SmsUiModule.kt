package com.mamy.android.di

import com.mamy.android.data.sms.RealSentSmsRepository
import com.mamy.android.data.sms.SentSmsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the [SentSmsRepository] used by [PersonDetailViewModel]
 * (SMS tab) and [SmsHistoryViewModel].
 *
 * Bound to [RealSentSmsRepository] — Room-backed implementation that wraps W1-E's
 * [com.mamy.android.data.db.dao.SentSmsDao]. Replaces the W1-B isolated-branch
 * stub `EmptySentSmsRepository` post-Wave-1 merge.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SmsUiModule {

    @Binds
    @Singleton
    abstract fun bindSentSmsRepository(impl: RealSentSmsRepository): SentSmsRepository
}
