package com.mamy.android.di

import com.mamy.android.domain.contacts.ContactMatcher
import com.mamy.android.domain.contacts.EmptyContactMatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * P9 SMS Hilt wiring. Bindings provided here :
 *  - [ContactMatcher] -> [EmptyContactMatcher] stub. **W1-D's merge replaces
 *    this binding** with the real cascade matcher backed by ContactsRepository
 *    + PersonDao.
 *
 * SmsSender, VoiceConfirmListener, SmsStatusReceiver, TextToHandler are all
 * `@Inject constructor` + `@Singleton` so Hilt finds them without an explicit
 * binding here. [java.time.Clock] is already provided by `LlmModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {

    @Binds
    @Singleton
    abstract fun bindContactMatcher(impl: EmptyContactMatcher): ContactMatcher
}
