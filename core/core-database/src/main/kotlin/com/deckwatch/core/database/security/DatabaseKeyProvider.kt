package com.deckwatch.core.database.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supplies the SQLCipher passphrase for the database — MASTER_PROMPT §18.
 *
 * The returned array is the raw key material. Callers hand it straight to SQLCipher and must not
 * log it, copy it into a `String`, or write it anywhere.
 */
fun interface DatabaseKeyProvider {
    fun passphrase(): ByteArray
}

/**
 * The production [DatabaseKeyProvider]: a random database key, wrapped by a key that never leaves
 * the Android Keystore.
 *
 * How it works, and why:
 * 1. On first use a 32-byte key is drawn from [SecureRandom]. That is the SQLCipher passphrase —
 *    it is never derived from anything the user types, so there is no passphrase to forget and no
 *    weak-password problem.
 * 2. That key is encrypted (AES/GCM) with a 256-bit AES key generated **inside** the Android
 *    Keystore under the alias [KEY_ALIAS]. On a device with a hardware-backed keystore the
 *    wrapping key is not extractable, so a copy of the app's data directory taken off the device
 *    is useless without the device.
 * 3. The GCM ciphertext and its IV are stored, Base64-encoded, in a private `SharedPreferences`
 *    file. GCM authenticates as well as encrypts, so a tampered blob fails to unwrap instead of
 *    yielding a wrong key and a corrupted database.
 * 4. Every subsequent open unwraps the same key.
 *
 * The write uses `commit()`, not `apply()`: if the process died between an asynchronous write and
 * the first database open, the wrapped key would be lost and the encrypted database unreadable —
 * exactly the silent data loss C10 forbids.
 *
 * The Keystore entry is deliberately created **without** user-authentication requirements. The
 * optional biometric/PIN app lock of §18 gates the UI, not the file: a background due-date
 * recomputation (§11.2) has to be able to open the database while the phone is locked.
 */
class KeystoreDatabaseKeyProvider(
    context: Context,
) : DatabaseKeyProvider {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Synchronised because two components may open the database concurrently on first run; without
     * it both could generate a key and the second would overwrite the one the first encrypted the
     * database with.
     */
    @Synchronized
    override fun passphrase(): ByteArray {
        val store = prefs
        val wrapped = store.getString(PREF_WRAPPED_KEY, null)
        val iv = store.getString(PREF_WRAP_IV, null)
        return if (wrapped != null && iv != null) {
            unwrap(decodeBase64(wrapped), decodeBase64(iv))
        } else {
            generateAndPersist(store)
        }
    }

    private fun generateAndPersist(store: SharedPreferences): ByteArray {
        val raw = ByteArray(DATABASE_KEY_BYTES)
        SecureRandom().nextBytes(raw)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val wrapped = cipher.doFinal(raw)

        store.edit()
            .putString(PREF_WRAPPED_KEY, encodeBase64(wrapped))
            .putString(PREF_WRAP_IV, encodeBase64(cipher.iv))
            .commit()

        return raw
    }

    private fun unwrap(wrapped: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(wrapped)
    }

    /** Returns the Keystore wrapping key, generating it on first use. */
    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(WRAPPING_KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        /** Android Keystore alias of the wrapping key. */
        const val KEY_ALIAS: String = "deckwatch_db_key"

        /** Private preferences file holding only the wrapped key and its IV. */
        const val PREFS_NAME: String = "deckwatch_db_key"

        internal const val PREF_WRAPPED_KEY = "wrapped_key"
        internal const val PREF_WRAP_IV = "wrap_iv"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val WRAPPING_KEY_BITS = 256
        private const val DATABASE_KEY_BYTES = 32
    }
}
