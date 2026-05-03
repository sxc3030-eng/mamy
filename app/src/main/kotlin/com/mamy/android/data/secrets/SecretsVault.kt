package com.mamy.android.data.secrets

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts arbitrary string secrets (BYOK API keys) and a stable 32-byte DB passphrase
 * using the AndroidKeystore master key from [KeystoreHelper].
 *
 * Storage: a private SharedPreferences file `mamy_vault.prefs`. We store
 * `Base64(IV || ciphertext)` per logical key. The actual AES key never leaves
 * the keystore.
 *
 * Threading: thread-safe via SharedPreferences synchronisation; cipher operations are
 * stateless (new Cipher per call).
 */
class SecretsVault(
    context: Context,
    private val keystoreHelper: KeystoreHelper,
) {

    private val prefs = context.getSharedPreferences(VAULT_FILE, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun putSecret(key: String, value: String) {
        require(key.isNotBlank()) { "key must not be blank" }
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keystoreHelper.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = iv + cipherText
        prefs.edit().putString(prefKey(key), Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun getSecret(key: String): String? {
        val encoded = prefs.getString(prefKey(key), null) ?: return null
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        if (blob.size < GCM_IV_BYTES + GCM_TAG_BYTES) return null
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreHelper.getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    fun deleteSecret(key: String) {
        prefs.edit().remove(prefKey(key)).apply()
    }

    /** Suspend wrapper around [getSecret], used by LLM-layer code that prefers suspend access. */
    suspend fun getKey(provider: String): String? = getSecret(provider)

    /** Suspend wrapper around [putSecret]. */
    suspend fun setKey(provider: String, key: String) = putSecret(provider, key)

    /**
     * Returns a stable 32-byte passphrase used to open SQLCipher. Generated once on first call,
     * persisted (encrypted) inside the vault, returned identically on subsequent calls.
     */
    fun getOrCreateDbPassphrase(): ByteArray {
        val existing = prefs.getString(prefKey(DB_PASSPHRASE_KEY), null)
        if (existing != null) {
            return Base64.decode(getSecret(DB_PASSPHRASE_KEY), Base64.NO_WRAP)
        }
        val passphrase = ByteArray(DB_PASSPHRASE_BYTES).also { random.nextBytes(it) }
        putSecret(DB_PASSPHRASE_KEY, Base64.encodeToString(passphrase, Base64.NO_WRAP))
        return passphrase
    }

    private fun prefKey(key: String): String = "secret::$key"

    companion object {
        private const val VAULT_FILE = "mamy_vault.prefs"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val DB_PASSPHRASE_KEY = "db_passphrase"
        private const val DB_PASSPHRASE_BYTES = 32
    }
}
