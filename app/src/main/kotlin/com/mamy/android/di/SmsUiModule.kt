package com.mamy.android.di

import com.mamy.android.data.sms.EmptySentSmsRepository
import com.mamy.android.data.sms.SentSmsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the [SentSmsRepository] used by [PersonDetailViewModel]
 * for the SMS tab.
 *
 * For W1-B (this branch) the binding is [EmptySentSmsRepository] — a no-op
 * that returns an empty flow so the SMS tab renders an empty state instead
 * of crashing. The real Room-backed impl ships in W1-E (parallel agent) and
 * will replace this provider at merge time. Renaming this module to
 * [SmsUiModule] (rather than `SmsModule`) avoids a name clash with the
 * `SmsModule.kt` planned in P9 spec for `SmsSender` + `SentSmsDao`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SmsUiModule {

    @Provides
    @Singleton
    fun provideSentSmsRepository(): SentSmsRepository = EmptySentSmsRepository()
}
