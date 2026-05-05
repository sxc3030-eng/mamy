package com.mamy.android.di

import android.content.Context
import com.mamy.android.data.contacts.ContactMatcher
import com.mamy.android.data.contacts.ContactsRepository
import com.mamy.android.data.db.dao.PersonDao
import com.mamy.android.domain.contacts.AutoLinkPersonContactsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContactsModule {

    @Provides
    @Singleton
    fun provideContactsRepository(@ApplicationContext context: Context): ContactsRepository =
        ContactsRepository(context)

    @Provides
    fun provideContactMatcher(
        personDao: PersonDao,
        contactsRepository: ContactsRepository,
    ): ContactMatcher = ContactMatcher(personDao, contactsRepository)

    @Provides
    @Singleton
    fun provideAutoLinkPersonContactsUseCase(
        personDao: PersonDao,
        contactsRepository: ContactsRepository,
    ): AutoLinkPersonContactsUseCase = AutoLinkPersonContactsUseCase(personDao, contactsRepository)
}
