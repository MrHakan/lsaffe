package com.deckwatch.core.database.security

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Builds the SQLCipher-backed [SupportSQLiteOpenHelper.Factory] that encrypts the database at
 * rest — MASTER_PROMPT §18, constraint C2.
 *
 * `net.zetetic:sqlcipher-android` does **not** load its native library for you: unlike the older
 * `android-database-sqlcipher` artifact there is no `SQLiteDatabase.loadLibs(context)` call, so
 * `System.loadLibrary("sqlcipher")` has to run before the factory is constructed or the first
 * database open throws `UnsatisfiedLinkError`.
 */
object SqlCipherOpenHelperFactoryProvider {

    private val nativeLibraryLoaded = AtomicBoolean(false)

    /**
     * Loads the native library once per process and returns a factory holding the passphrase from
     * [keyProvider].
     */
    fun create(keyProvider: DatabaseKeyProvider): SupportSQLiteOpenHelper.Factory {
        loadNativeLibrary()
        return SupportOpenHelperFactory(keyProvider.passphrase())
    }

    /**
     * Idempotent. `System.loadLibrary` is itself safe to call twice, but the flag keeps the cost
     * off the cold-start path (§17.3 budgets 1.5 s) after the first open.
     */
    fun loadNativeLibrary() {
        if (nativeLibraryLoaded.compareAndSet(false, true)) {
            System.loadLibrary("sqlcipher")
        }
    }
}
