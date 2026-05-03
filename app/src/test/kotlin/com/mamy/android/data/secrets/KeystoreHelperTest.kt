package com.mamy.android.data.secrets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyStore

/**
 * NOTE: Robolectric does not shim Android Keystore (`NoSuchAlgorithmException` for `AndroidKeyStore`).
 * These tests are kept as @Ignore so they document expected behaviour and can be promoted to
 * `androidTest` (instrumented) once an emulator/device is available. P2 will move them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeystoreHelperTest {

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `getOrCreateMasterKey returns non-null SecretKey`() {
        val helper = KeystoreHelper()
        val key = helper.getOrCreateMasterKey()
        assertNotNull("Master key must not be null", key)
        assertEquals("Master key must be AES", "AES", key.algorithm)
    }

    @Test
    @Ignore("Requires real Android Keystore — run as androidTest on emulator/device")
    fun `calling getOrCreateMasterKey twice returns same key alias`() {
        val helper = KeystoreHelper()
        helper.getOrCreateMasterKey()
        helper.getOrCreateMasterKey()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(
            "Master key alias must persist after multiple calls",
            ks.containsAlias(KeystoreHelper.MASTER_KEY_ALIAS)
        )
    }
}
