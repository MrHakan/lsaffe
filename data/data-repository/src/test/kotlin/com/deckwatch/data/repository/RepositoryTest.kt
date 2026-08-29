package com.deckwatch.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.RoomTransactionRunner
import com.deckwatch.core.database.TransactionRunner
import com.deckwatch.core.database.createInMemoryDeckWatchDatabase
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.testing.TestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base for the repository tests.
 *
 * The repositories are exercised against a **real, unencrypted, in-memory** DeckWatch database and
 * a real Preferences DataStore over a temporary file: the DAOs, the type converters and the
 * settings serialisation under test are the production ones, only the file layer differs. What is
 * substituted is exactly the ambient state — [FixedIdFactory] and [FixedClock] — so a test can
 * assert on literal ids and dates.
 *
 * The SDK is pinned for the same reason as the DAO tests: `compileSdk` is 36 and Robolectric ships
 * no runtime for it yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
abstract class RepositoryTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    protected lateinit var database: DeckWatchDatabase
    protected lateinit var preferences: UserPreferencesRepository
    protected lateinit var transaction: TransactionRunner

    protected val ids = FixedIdFactory()
    protected val clock = FixedClock()

    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun createFixtures() {
        database = createInMemoryDeckWatchDatabase(ApplicationProvider.getApplicationContext())
        transaction = RoomTransactionRunner(database)
        storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        preferences = UserPreferencesRepository(dataStore)
    }

    @After
    fun releaseFixtures() {
        storeScope.cancel()
        database.close()
    }

    protected fun vesselRepository(): RoomVesselRepository = RoomVesselRepository(
        vesselDao = database.vesselDao(),
        deckDao = database.deckDao(),
        zoneDao = database.zoneDao(),
        categoryDao = database.categoryDao(),
        equipmentDao = database.equipmentDao(),
        taskInstanceDao = database.taskInstanceDao(),
        roundDao = database.roundDao(),
        roundItemDao = database.roundItemDao(),
        deficiencyDao = database.deficiencyDao(),
        idFactory = ids,
        clock = clock,
        transaction = transaction,
    )

    protected fun equipmentRepository(): RoomEquipmentRepository = RoomEquipmentRepository(
        equipmentDao = database.equipmentDao(),
        idFactory = ids,
        clock = clock,
    )

    protected fun inspectionRepository(): RoomInspectionRepository = RoomInspectionRepository(
        roundDao = database.roundDao(),
        roundItemDao = database.roundItemDao(),
        deficiencyDao = database.deficiencyDao(),
    )

    protected fun maintenanceRepository(): RoomMaintenanceRepository = RoomMaintenanceRepository(
        taskDefinitionDao = database.taskDefinitionDao(),
        taskInstanceDao = database.taskInstanceDao(),
        equipmentDao = database.equipmentDao(),
        equipmentTypeDao = database.equipmentTypeDao(),
        vesselDao = database.vesselDao(),
        preferences = preferences,
        engine = DueEngine { clock.today },
        transaction = transaction,
        clock = clock,
    )
}

/** Ids a test can predict: `generated-1`, `generated-2`, … */
class FixedIdFactory : IdFactory {
    private var counter = 0
    override fun newId(): String = "generated-${++counter}"
}

/** A clock pinned to [TestData.referenceDay], so nothing under test drifts with the wall clock. */
class FixedClock(
    var today: Long = TestData.referenceDay,
    var now: Long = TestData.referenceMillis,
) : AppClock {
    override fun nowMillis(): Long = now
    override fun todayEpochDay(): Long = today
}

internal const val ROBOLECTRIC_SDK = 34
