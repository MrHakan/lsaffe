package com.deckwatch.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Builds the on-disk database.
 *
 * [openHelperFactory] is the **only** difference between production and test. Production passes
 * the SQLCipher factory from
 * [com.deckwatch.core.database.security.SqlCipherOpenHelperFactoryProvider]; unit tests pass
 * `null` and get the platform's own unencrypted SQLite, so no test needs the native SQLCipher
 * library on the JVM and there is still only one schema and one set of DAOs.
 *
 * No `fallbackToDestructiveMigration` anywhere: C10 forbids silently losing data, so a missing
 * migration must fail loudly rather than wipe a vessel's register.
 */
fun createDeckWatchDatabase(
    context: Context,
    name: String = DeckWatchDatabase.NAME,
    openHelperFactory: SupportSQLiteOpenHelper.Factory? = null,
): DeckWatchDatabase =
    Room.databaseBuilder(context.applicationContext, DeckWatchDatabase::class.java, name)
        .apply { openHelperFactory?.let { openHelperFactory(it) } }
        .build()

/**
 * An unencrypted in-memory database for tests and for `core-testing` fixtures.
 *
 * Queries are allowed on the main thread because a test body is not a UI thread; production code
 * must never call this.
 */
fun createInMemoryDeckWatchDatabase(context: Context): DeckWatchDatabase =
    Room.inMemoryDatabaseBuilder(context.applicationContext, DeckWatchDatabase::class.java)
        .allowMainThreadQueries()
        .build()
