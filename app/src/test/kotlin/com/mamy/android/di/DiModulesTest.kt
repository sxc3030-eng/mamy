package com.mamy.android.di

import dagger.Module
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Smoke test : the three DI modules are annotated with @Module (RUNTIME retention).
 *
 * NOTE: @InstallIn has CLASS retention (not RUNTIME), so cannot be reflected at unit-test
 * time. The full Hilt graph wire-up is verified later by @HiltAndroidTest instrumented
 * tests in P2 (require emulator).
 */
class DiModulesTest {

    @Test
    fun `DatabaseModule is annotated @Module`() {
        assertNotNull(DatabaseModule::class.java.getAnnotation(Module::class.java))
    }

    @Test
    fun `SecretsModule is annotated @Module`() {
        assertNotNull(SecretsModule::class.java.getAnnotation(Module::class.java))
    }

    @Test
    fun `SettingsModule is annotated @Module`() {
        assertNotNull(SettingsModule::class.java.getAnnotation(Module::class.java))
    }
}
