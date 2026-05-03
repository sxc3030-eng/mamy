package com.mamy.android.di

import android.content.Context
import com.mamy.android.data.secrets.KeystoreHelper
import com.mamy.android.data.secrets.SecretsVault
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecretsModule {

    @Provides
    @Singleton
    fun provideKeystoreHelper(): KeystoreHelper = KeystoreHelper()

    @Provides
    @Singleton
    fun provideSecretsVault(
        @ApplicationContext context: Context,
        keystoreHelper: KeystoreHelper,
    ): SecretsVault = SecretsVault(context, keystoreHelper)
}
