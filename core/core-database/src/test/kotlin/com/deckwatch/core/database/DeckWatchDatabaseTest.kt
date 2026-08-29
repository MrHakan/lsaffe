package com.deckwatch.core.database

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base for the DAO tests.
 *
 * The database is built **unencrypted and in memory**: SQLCipher is a native library and cannot
 * load on the JVM, so the production open-helper factory is simply not passed
 * ([createInMemoryDeckWatchDatabase] takes none). That is the whole reason
 * [createDeckWatchDatabase] takes an optional factory — the schema, the DAOs and the converters
 * under test are byte-for-byte the ones that run in production; only the file layer differs.
 *
 * The SDK is pinned because `compileSdk` is 36 and Robolectric ships no runtime for it yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
abstract class DeckWatchDatabaseTest {

    protected lateinit var database: DeckWatchDatabase

    @Before
    fun createDatabase() {
        database = createInMemoryDeckWatchDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun closeDatabase() {
        database.close()
    }
}

internal const val ROBOLECTRIC_SDK = 34
