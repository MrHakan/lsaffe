package com.deckwatch.core.database.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.createDeckWatchDatabase
import com.deckwatch.core.database.security.DatabaseKeyProvider
import com.deckwatch.core.database.security.KeystoreDatabaseKeyProvider
import com.deckwatch.core.database.security.SqlCipherOpenHelperFactoryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the production, encrypted database — MASTER_PROMPT §18.
 *
 * The open-helper factory is a separate binding on purpose: a test graph replaces just that one
 * binding (`@TestInstallIn`) to get the same schema unencrypted, and nothing else in the graph
 * changes. Unit tests that need no graph at all use
 * `com.deckwatch.core.database.createInMemoryDeckWatchDatabase` directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyProvider(
        @ApplicationContext context: Context,
    ): DatabaseKeyProvider = KeystoreDatabaseKeyProvider(context)

    @Provides
    @Singleton
    fun provideOpenHelperFactory(
        keyProvider: DatabaseKeyProvider,
    ): SupportSQLiteOpenHelper.Factory = SqlCipherOpenHelperFactoryProvider.create(keyProvider)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        openHelperFactory: SupportSQLiteOpenHelper.Factory,
    ): DeckWatchDatabase = createDeckWatchDatabase(
        context = context,
        openHelperFactory = openHelperFactory,
    )
}
