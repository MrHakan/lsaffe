package com.deckwatch.feature.notes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The disclaimer strip is a safety notice, so "dismissed" has to survive the app being closed —
 * and, just as importantly, must not come back on the next launch as if the tap never happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesChromeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun createStore() {
        storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        preferences = UserPreferencesRepository(dataStore)
    }

    @After
    fun closeStore() {
        storeScope.cancel()
    }

    @Test
    fun `the strip shows until it is dismissed, and stays dismissed afterwards`() = runTest {
        val viewModel = NotesChromeViewModel(preferences)
        assertThat(viewModel.footerVisible.first { it }).isTrue()

        viewModel.dismissFooter()

        assertThat(viewModel.footerVisible.first { !it }).isFalse()
        // A fresh view model is what the next launch builds: the answer has to come from the store.
        assertThat(NotesChromeViewModel(preferences).footerVisible.first { !it }).isFalse()
        assertThat(preferences.get().notesFooterDismissed).isTrue()
    }
}
