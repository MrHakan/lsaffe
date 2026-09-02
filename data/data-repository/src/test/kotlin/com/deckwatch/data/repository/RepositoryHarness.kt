package com.deckwatch.data.repository

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.createInMemoryDeckWatchDatabase
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.seed.SeedDataSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The real stack, wired by hand: the **real** [DeckWatchDatabase] (in memory, through
 * `core-database`'s own unencrypted factory path — the production one differs only in its
 * open-helper), the real DAOs, the real `UserPreferencesRepository` over a real Preferences
 * DataStore file, the real [DueEngine], and the real seed assets.
 *
 * Nothing here is a fake except the clock, which is frozen so a due-date assertion means the same
 * thing on 31 January as on 1 February.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryHarness(
    settingsDirectory: File,
    todayEpochDay: Long,
) {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider(
        main = UnconfinedTestDispatcher(),
        io = UnconfinedTestDispatcher(),
        default = UnconfinedTestDispatcher(),
    )

    val time: TimeSource = FixedTimeSource(todayEpochDay)

    val database: DeckWatchDatabase = createInMemoryDeckWatchDatabase(context)

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = storeScope,
        produceFile = { File(settingsDirectory, "settings.preferences_pb") },
    )

    val preferences = UserPreferencesRepository(dataStore)

    val seedDataSource = SeedDataSource(context)

    val vesselRepository = VesselRepositoryImpl(
        database = database,
        vesselDao = database.vesselDao(),
        deckDao = database.deckDao(),
        zoneDao = database.zoneDao(),
        categoryDao = database.categoryDao(),
        equipmentDao = database.equipmentDao(),
        taskInstanceDao = database.taskInstanceDao(),
        deficiencyDao = database.deficiencyDao(),
        roundDao = database.roundDao(),
        roundItemDao = database.roundItemDao(),
        preferences = preferences,
        dispatchers = dispatchers,
        time = time,
    )

    val equipmentRepository = EquipmentRepositoryImpl(
        database = database,
        equipmentDao = database.equipmentDao(),
        dispatchers = dispatchers,
        time = time,
    )

    val maintenanceRepository = MaintenanceRepositoryImpl(
        database = database,
        equipmentDao = database.equipmentDao(),
        equipmentTypeDao = database.equipmentTypeDao(),
        taskDefinitionDao = database.taskDefinitionDao(),
        taskInstanceDao = database.taskInstanceDao(),
        vesselDao = database.vesselDao(),
        preferences = preferences,
        engine = DueEngine { todayEpochDay },
        dispatchers = dispatchers,
        time = time,
    )

    val inspectionRepository = InspectionRepositoryImpl(
        roundDao = database.roundDao(),
        roundItemDao = database.roundItemDao(),
        deficiencyDao = database.deficiencyDao(),
        dispatchers = dispatchers,
    )

    val referenceRepository = ReferenceRepositoryImpl(
        regulationCardDao = database.regulationCardDao(),
        equipmentTypeDao = database.equipmentTypeDao(),
        roundTemplateDao = database.roundTemplateDao(),
        userNoteDao = database.userNoteDao(),
        seedDataSource = seedDataSource,
        dispatchers = dispatchers,
    )

    val seedInitializer = SeedInitializer(
        context = context,
        database = database,
        seedDataSource = seedDataSource,
        equipmentTypeDao = database.equipmentTypeDao(),
        taskDefinitionDao = database.taskDefinitionDao(),
        regulationCardDao = database.regulationCardDao(),
        roundTemplateDao = database.roundTemplateDao(),
        dispatchers = dispatchers,
    )

    val demoVesselInstaller = DemoVesselInstaller(
        database = database,
        seedDataSource = seedDataSource,
        vesselDao = database.vesselDao(),
        deckDao = database.deckDao(),
        zoneDao = database.zoneDao(),
        equipmentDao = database.equipmentDao(),
        taskInstanceDao = database.taskInstanceDao(),
        deficiencyDao = database.deficiencyDao(),
        maintenanceRepository = maintenanceRepository,
        preferences = preferences,
        dispatchers = dispatchers,
        time = time,
    )

    /** Row count of any table, for the "nothing was left behind" assertions. */
    fun countOf(table: String): Int =
        database.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun close() {
        database.close()
        storeScope.cancel()
    }
}
