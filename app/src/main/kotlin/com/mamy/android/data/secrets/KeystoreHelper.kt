package com.mamy.android.data.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.KeyStore

/**
 * Wraps Android Keystore for the MamY master AES-256 key. Hardware-backed when device supports
 * StrongBox or TEE; otherwise software-backed inside the AndroidKeyStore daemon.
 *
 * Master key is used by [SecretsVault] to encrypt:
 *  - BYOK API keys (Claude / OpenAI / Gemini)
 *  - SQLCipher DB passphrase
 *
 * Never serialised, never leaves the keystore.
 */
class KeystoreHelper {

    fun getOrCreateMasterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (ks.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    companion object {
        const val MASTER_KEY_ALIAS = "mamy_master_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
    }
}
