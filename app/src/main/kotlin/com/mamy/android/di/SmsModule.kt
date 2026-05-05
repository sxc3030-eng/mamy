package com.mamy.android.di

import com.mamy.android.domain.contacts.ContactMatcher
import com.mamy.android.domain.contacts.ContactMatcherAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * P9 SMS Hilt wiring. Bindings provided here :
 *  - [ContactMatcher] (domain interface) -> [ContactMatcherAdapter] which delegates
 *    to W1-D's real `data.contacts.ContactMatcher` cascade (Person team → exact →
 *    substring → fuzzy). The adapter converts the data-layer `Contact` to the
 *    domain-layer `ContactCandidate` and picks the best phone (MOBILE > WORK > HOME).
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
    abstract fun bindContactMatcher(impl: ContactMatcherAdapter): ContactMatcher
}
