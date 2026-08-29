package com.deckwatch.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.deckwatch.core.datastore.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** File name of the Preferences DataStore holding the §18 settings. */
private const val SETTINGS_DATASTORE_NAME = "settings"

/**
 * Provides the single `settings` DataStore.
 *
 * The scope is an application-lifetime [CoroutineScope] with a [SupervisorJob], not `GlobalScope`:
 * one failed write must not cancel the store, and the store must outlive every screen that reads
 * it. A corrupted file is replaced with empty preferences instead of throwing — losing settings is
 * recoverable, refusing to start is not.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) },
    )

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        dataStore: DataStore<Preferences>,
    ): UserPreferencesRepository = UserPreferencesRepository(dataStore)
}
