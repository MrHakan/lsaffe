package com.deckwatch.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
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
import java.io.File

/**
 * Plain JVM tests — no Robolectric. The Preferences DataStore is built over a real file in a
 * temporary folder, so what is exercised is the actual serialisation, not a fake.
 *
 * The opt-in is for [UnconfinedTestDispatcher], which is still experimental API in
 * kotlinx-coroutines-test. It is the right dispatcher here: DataStore's writes must complete
 * eagerly so the very next read in a test body sees them, without any manual scheduler advancing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

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
            // Not TemporaryFolder.newFile(): DataStore must create the file itself, an existing
            // zero-byte file reads back as corrupt.
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        repository = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `an empty store yields the documented defaults`() = runTest {
        val prefs = repository.get()

        assertThat(prefs).isEqualTo(UserPreferences())
        assertThat(prefs.themeMode).isEqualTo(ThemeMode.DAY)
        assertThat(prefs.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(prefs.density).isEqualTo(ListDensity.COMPACT)
        assertThat(prefs.dueLeadTimeDays).isEqualTo(30)
        assertThat(prefs.notificationHour).isEqualTo(8)
        assertThat(prefs.notificationMinute).isEqualTo(0)
        assertThat(prefs.notificationsEnabled).isTrue()
        assertThat(prefs.defaultFlag).isEqualTo(FlagState.OTHER)
        assertThat(prefs.isoAngleDeg).isEqualTo(30f)
        assertThat(prefs.gridSnapEnabled).isFalse()
        assertThat(prefs.tagNumberFormat).isEqualTo("{PREFIX}-{DECK}-{NNN}")
        assertThat(prefs.photoQuality).isEqualTo(PhotoQuality.MEDIUM)
        assertThat(prefs.metricUnits).isTrue()
        assertThat(prefs.firstDayOfWeek).isEqualTo(1)
        assertThat(prefs.activeVesselId).isNull()
        assertThat(prefs.onboardingDone).isFalse()
        assertThat(prefs.disclaimerAccepted).isFalse()
        assertThat(prefs.lastBackupAt).isNull()
        assertThat(prefs.firstRunAt).isEqualTo(0L)
    }

    @Test
    fun `every setting round-trips`() = runTest {
        repository.setThemeMode(ThemeMode.BRIDGE)
        repository.setThemeFollowSchedule(true)
        repository.setLanguage(AppLanguage.TURKISH)
        repository.setDensity(ListDensity.COMFORTABLE)
        repository.setDueLeadTimeDays(45)
        repository.setNotificationTime(hour = 6, minute = 30)
        repository.setNotificationsEnabled(false)
        repository.setDefaultFlag(FlagState.LIBERIA)
        repository.setIsoAngleDeg(22.5f)
        repository.setGridSnapEnabled(true)
        repository.setTagNumberFormat("{PREFIX}{NNN}")
        repository.setPhotoQuality(PhotoQuality.HIGH)
        repository.setMetricUnits(false)
        repository.setFirstDayOfWeek(7)
        repository.setActiveVesselId("vessel-42")
        repository.setOnboardingDone(true)
        repository.setDisclaimerAccepted(true)
        repository.setLastBackupAt(1_700_000_000_000L)
        repository.setFirstRunAt(1_600_000_000_000L)

        assertThat(repository.get()).isEqualTo(
            UserPreferences(
                themeMode = ThemeMode.BRIDGE,
                themeFollowSchedule = true,
                language = AppLanguage.TURKISH,
                density = ListDensity.COMFORTABLE,
                dueLeadTimeDays = 45,
                notificationHour = 6,
                notificationMinute = 30,
                notificationsEnabled = false,
                defaultFlag = FlagState.LIBERIA,
                isoAngleDeg = 22.5f,
                gridSnapEnabled = true,
                tagNumberFormat = "{PREFIX}{NNN}",
                photoQuality = PhotoQuality.HIGH,
                metricUnits = false,
                firstDayOfWeek = 7,
                activeVesselId = "vessel-42",
                onboardingDone = true,
                disclaimerAccepted = true,
                lastBackupAt = 1_700_000_000_000L,
                firstRunAt = 1_600_000_000_000L,
            ),
        )
    }

    @Test
    fun `the flow emits each change as it is written`() = runTest {
        assertThat(repository.userPreferences.first().themeMode).isEqualTo(ThemeMode.DAY)

        repository.setThemeMode(ThemeMode.NIGHT)
        assertThat(repository.userPreferences.first().themeMode).isEqualTo(ThemeMode.NIGHT)

        repository.setThemeMode(ThemeMode.BRIDGE)
        assertThat(repository.userPreferences.first().themeMode).isEqualTo(ThemeMode.BRIDGE)
    }

    @Test
    fun `the isometric angle is clamped to the renderable range`() = runTest {
        repository.setIsoAngleDeg(90f)
        assertThat(repository.get().isoAngleDeg).isEqualTo(35f)

        repository.setIsoAngleDeg(-10f)
        assertThat(repository.get().isoAngleDeg).isEqualTo(0f)

        repository.setIsoAngleDeg(30f)
        assertThat(repository.get().isoAngleDeg).isEqualTo(30f)
    }

    @Test
    fun `out-of-range times, lead times and week days are coerced rather than stored`() = runTest {
        repository.setNotificationTime(hour = 30, minute = 99)
        repository.setDueLeadTimeDays(-5)
        repository.setFirstDayOfWeek(12)

        val prefs = repository.get()
        assertThat(prefs.notificationHour).isEqualTo(23)
        assertThat(prefs.notificationMinute).isEqualTo(59)
        assertThat(prefs.dueLeadTimeDays).isEqualTo(0)
        assertThat(prefs.firstDayOfWeek).isEqualTo(7)
    }

    @Test
    fun `a blank tag format and an unknown photo quality fall back to the defaults`() = runTest {
        repository.setTagNumberFormat("   ")
        repository.setPhotoQuality("ULTRA")

        val prefs = repository.get()
        assertThat(prefs.tagNumberFormat).isEqualTo("{PREFIX}-{DECK}-{NNN}")
        assertThat(prefs.photoQuality).isEqualTo(PhotoQuality.MEDIUM)
    }

    @Test
    fun `the active vessel can be selected and cleared`() = runTest {
        repository.setActiveVesselId("vessel-1")
        assertThat(repository.get().activeVesselId).isEqualTo("vessel-1")

        repository.setActiveVesselId(null)
        assertThat(repository.get().activeVesselId).isNull()

        repository.setActiveVesselId("  ")
        assertThat(repository.get().activeVesselId).isNull()
    }

    @Test
    fun `the last backup timestamp can be cleared back to never`() = runTest {
        repository.setLastBackupAt(1_700_000_000_000L)
        assertThat(repository.get().lastBackupAt).isEqualTo(1_700_000_000_000L)

        repository.setLastBackupAt(null)
        assertThat(repository.get().lastBackupAt).isNull()
    }

    @Test
    fun `markFirstRun records the first launch once and never moves it`() = runTest {
        repository.markFirstRun(1_600_000_000_000L)
        repository.markFirstRun(1_900_000_000_000L)

        assertThat(repository.get().firstRunAt).isEqualTo(1_600_000_000_000L)
    }

    @Test
    fun `clear restores every default`() = runTest {
        repository.setThemeMode(ThemeMode.NIGHT)
        repository.setActiveVesselId("vessel-1")
        repository.setOnboardingDone(true)

        repository.clear()

        assertThat(repository.get()).isEqualTo(UserPreferences())
    }
}
