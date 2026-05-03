package com.mamy.android.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DiModulesTest {

    @Test
    fun `DatabaseModule is annotated @Module @InstallIn(SingletonComponent)`() {
        assertNotNull(DatabaseModule::class.java.getAnnotation(Module::class.java))
        val install = DatabaseModule::class.java.getAnnotation(InstallIn::class.java)
        assertNotNull(install)
        assertEquals(SingletonComponent::class.java, install!!.value[0].java)
    }

    @Test
    fun `SecretsModule is annotated @Module @InstallIn(SingletonComponent)`() {
        assertNotNull(SecretsModule::class.java.getAnnotation(Module::class.java))
        val install = SecretsModule::class.java.getAnnotation(InstallIn::class.java)
        assertNotNull(install)
        assertEquals(SingletonComponent::class.java, install!!.value[0].java)
    }

    @Test
    fun `SettingsModule is annotated @Module @InstallIn(SingletonComponent)`() {
        assertNotNull(SettingsModule::class.java.getAnnotation(Module::class.java))
        val install = SettingsModule::class.java.getAnnotation(InstallIn::class.java)
        assertNotNull(install)
        assertEquals(SingletonComponent::class.java, install!!.value[0].java)
    }
}
