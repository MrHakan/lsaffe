package com.deckwatch.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.deckwatch.core.datastore.PhotoQuality
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
import com.deckwatch.feature.settings.settings.DUE_LEAD_TIME_RANGE
import com.deckwatch.feature.settings.settings.SettingsViewModel
import com.deckwatch.feature.settings.settings.tagFormatExample
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Write-through for every §18 setter.
 *
 * The DataStore is real, over a file in a temporary folder — the same approach `core-datastore` and
 * `feature-deckview` take. A fake would prove only that the view model calls a method; this proves
 * the value survives serialisation and comes back through the flow the screen reads, which is where
 * a mistyped key or a lost enum name would actually show up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        repository = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private fun viewModel() = SettingsViewModel(repository)

    @Test
    fun `theme mode writes through`() = runTest {
        val vm = viewModel()
        vm.setThemeMode(ThemeMode.BRIDGE)
        advanceUntilIdle()
        assertThat(repository.get().themeMode).isEqualTo(ThemeMode.BRIDGE)
    }

    @Test
    fun `theme follow schedule writes through`() = runTest {
        val vm = viewModel()
        vm.setThemeFollowSchedule(true)
        advanceUntilIdle()
        assertThat(repository.get().themeFollowSchedule).isTrue()
    }

    @Test
    fun `language writes through`() = runTest {
        val vm = viewModel()
        vm.setLanguage(AppLanguage.TURKISH)
        advanceUntilIdle()
        assertThat(repository.get().language).isEqualTo(AppLanguage.TURKISH)
    }

    @Test
    fun `density writes through`() = runTest {
        val vm = viewModel()
        vm.setDensity(ListDensity.COMFORTABLE)
        advanceUntilIdle()
        assertThat(repository.get().density).isEqualTo(ListDensity.COMFORTABLE)
    }

    @Test
    fun `due lead time writes through`() = runTest {
        val vm = viewModel()
        vm.setDueLeadTimeDays(45)
        advanceUntilIdle()
        assertThat(repository.get().dueLeadTimeDays).isEqualTo(45)
    }

    @Test
    fun `notification time writes both fields`() = runTest {
        val vm = viewModel()
        vm.setNotificationTime(hour = 6, minute = 30)
        advanceUntilIdle()
        val prefs = repository.get()
        assertThat(prefs.notificationHour).isEqualTo(6)
        assertThat(prefs.notificationMinute).isEqualTo(30)
    }

    @Test
    fun `notifications enabled writes through`() = runTest {
        val vm = viewModel()
        vm.setNotificationsEnabled(false)
        advanceUntilIdle()
        assertThat(repository.get().notificationsEnabled).isFalse()
    }

    @Test
    fun `default flag writes through`() = runTest {
        val vm = viewModel()
        vm.setDefaultFlag(FlagState.LIBERIA)
        advanceUntilIdle()
        assertThat(repository.get().defaultFlag).isEqualTo(FlagState.LIBERIA)
    }

    @Test
    fun `isometric angle writes through and is clamped by the repository`() = runTest {
        val vm = viewModel()
        vm.setIsoAngleDeg(12f)
        advanceUntilIdle()
        assertThat(repository.get().isoAngleDeg).isEqualTo(12f)

        vm.setIsoAngleDeg(90f)
        advanceUntilIdle()
        assertThat(repository.get().isoAngleDeg).isEqualTo(35f)
    }

    @Test
    fun `grid snap writes through`() = runTest {
        val vm = viewModel()
        vm.setGridSnapEnabled(true)
        advanceUntilIdle()
        assertThat(repository.get().gridSnapEnabled).isTrue()
    }

    @Test
    fun `tag number format writes through and a blank falls back`() = runTest {
        val vm = viewModel()
        vm.setTagNumberFormat("{PREFIX}/{NNN}")
        advanceUntilIdle()
        assertThat(repository.get().tagNumberFormat).isEqualTo("{PREFIX}/{NNN}")

        vm.setTagNumberFormat("   ")
        advanceUntilIdle()
        assertThat(repository.get().tagNumberFormat).isEqualTo("{PREFIX}-{DECK}-{NNN}")
    }

    @Test
    fun `photo quality writes through and an unknown tier falls back`() = runTest {
        val vm = viewModel()
        vm.setPhotoQuality(PhotoQuality.HIGH)
        advanceUntilIdle()
        assertThat(repository.get().photoQuality).isEqualTo(PhotoQuality.HIGH)

        vm.setPhotoQuality("ULTRA")
        advanceUntilIdle()
        assertThat(repository.get().photoQuality).isEqualTo(PhotoQuality.MEDIUM)
    }

    @Test
    fun `units write through`() = runTest {
        val vm = viewModel()
        vm.setMetricUnits(false)
        advanceUntilIdle()
        assertThat(repository.get().metricUnits).isFalse()
    }

    @Test
    fun `first day of week writes through`() = runTest {
        val vm = viewModel()
        vm.setFirstDayOfWeek(7)
        advanceUntilIdle()
        assertThat(repository.get().firstDayOfWeek).isEqualTo(7)
    }

    @Test
    fun `lead time slider range is the 7 to 90 days the screen offers`() {
        assertThat(DUE_LEAD_TIME_RANGE.first).isEqualTo(7)
        assertThat(DUE_LEAD_TIME_RANGE.last).isEqualTo(90)
    }

    @Test
    fun `tag format example substitutes the documented placeholders`() {
        assertThat(tagFormatExample("{PREFIX}-{DECK}-{NNN}")).isEqualTo("FE-UD-003")
        assertThat(tagFormatExample("{PREFIX}{NN}")).isEqualTo("FE03")
        // An unknown placeholder is left visible rather than blanked, so a typo shows up as itself.
        assertThat(tagFormatExample("{SHIP}-{PREFIX}")).isEqualTo("{SHIP}-FE")
    }
}
