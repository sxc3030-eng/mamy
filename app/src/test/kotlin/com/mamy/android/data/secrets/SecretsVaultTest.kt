package com.mamy.android.data.secrets

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecretsVaultTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val vault = SecretsVault(ctx, KeystoreHelper())

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `put then get returns same string`() {
        vault.putSecret("claude_api_key", "sk-ant-test-12345")
        assertEquals("sk-ant-test-12345", vault.getSecret("claude_api_key"))
    }

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `get unknown key returns null`() {
        assertNull(vault.getSecret("does_not_exist"))
    }

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `two distinct keys do not collide`() {
        vault.putSecret("openai_api_key", "sk-aaa")
        vault.putSecret("gemini_api_key", "sk-bbb")
        assertEquals("sk-aaa", vault.getSecret("openai_api_key"))
        assertEquals("sk-bbb", vault.getSecret("gemini_api_key"))
    }

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `getOrCreateDbPassphrase returns 32 byte stable passphrase`() {
        val p1 = vault.getOrCreateDbPassphrase()
        val p2 = vault.getOrCreateDbPassphrase()
        assertEquals("DB passphrase must be 32 bytes", 32, p1.size)
        assertArrayEquals("DB passphrase must be stable across calls", p1, p2)
    }

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `regenerated vault still reads previously stored secrets`() {
        vault.putSecret("k", "value")
        val freshVault = SecretsVault(ctx, KeystoreHelper())
        assertEquals("value", freshVault.getSecret("k"))
    }

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `putSecret overwrite replaces previous value`() {
        vault.putSecret("rotate", "old")
        vault.putSecret("rotate", "new")
        assertNotEquals("old", vault.getSecret("rotate"))
        assertEquals("new", vault.getSecret("rotate"))
    }
}
