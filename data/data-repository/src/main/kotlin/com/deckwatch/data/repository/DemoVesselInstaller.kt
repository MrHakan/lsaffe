package com.deckwatch.data.repository

import androidx.room.withTransaction
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.dao.DeckDao
import com.deckwatch.core.database.dao.DeficiencyDao
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.dao.TaskInstanceDao
import com.deckwatch.core.database.dao.VesselDao
import com.deckwatch.core.database.dao.ZoneDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.seed.DemoVesselData
import com.deckwatch.data.seed.SeedDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Installs and removes the demo vessel — MASTER_PROMPT §14 ("load in one tap and delete in one
 * tap"), §19 item 6.
 *
 * **Deterministic ids.** `SeedDataSource.buildDemoVessel` derives every id from a fixed namespace
 * and the seed's own keys, so "MV Example"'s upper deck has the same id on every device and on
 * every load. [install] therefore *replaces* rather than duplicates: the previous copy is purged
 * first, in the same transaction that writes the new one, so a second tap on "Load demo vessel"
 * cannot leave two overlapping registers behind.
 *
 * **Dates are relative.** The seed stores day offsets, not dates, so the five overdue items in
 * §19's demo are still overdue whenever the demo is loaded — the whole point of shipping it.
 *
 * **Due state is computed, not seeded.** The seed's `nextDueDaysAhead` values are only a hint for
 * the marker colours before the engine has run; [install] finishes by calling
 * [MaintenanceRepository.recomputeDueForVessel], so the Due tab is populated from real task
 * instances derived from the catalogue — the same code path a real vessel goes through.
 *
 * The demo vessel becomes the active vessel on install (the officer has just asked to look at it),
 * and on uninstall the active selection falls back to whatever else the database holds.
 */
@Singleton
class DemoVesselInstaller @Inject constructor(
    private val database: DeckWatchDatabase,
    private val seedDataSource: SeedDataSource,
    private val vesselDao: VesselDao,
    private val deckDao: DeckDao,
    private val zoneDao: ZoneDao,
    private val equipmentDao: EquipmentDao,
    private val taskInstanceDao: TaskInstanceDao,
    private val deficiencyDao: DeficiencyDao,
    private val maintenanceRepository: MaintenanceRepository,
    private val preferences: UserPreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val time: TimeSource,
) : DemoVessel {

    private val mutex = Mutex()

    @Volatile
    private var cachedId: String? = null

    /** Materialises the demo vessel and returns its (fixed) id. */
    override suspend fun install(): String = withContext(dispatchers.io) {
        mutex.withLock {
            val data = buildDemo()
            installData(data)
            maintenanceRepository.recomputeDueForVessel(data.vessel.id)
            preferences.setActiveVesselId(data.vessel.id)
            data.vessel.id
        }
    }

    /** Removes the demo vessel and everything under it. A no-op when it is not installed. */
    override suspend fun uninstall() = withContext(dispatchers.io) {
        mutex.withLock {
            val id = demoVesselId()
            database.withTransaction {
                purge(id)
                vesselDao.deleteById(id)
            }
            if (preferences.get().activeVesselId == id) {
                preferences.setActiveVesselId(vesselDao.getActive()?.id)
            }
        }
    }

    override suspend fun isInstalled(): Boolean = withContext(dispatchers.io) {
        vesselDao.getById(demoVesselId()) != null
    }

    /**
     * The demo vessel's fixed id. Derived by materialising the seed once and then remembered, so
     * "is the demo installed?" does not re-parse 100 kB of JSON on every call.
     */
    override suspend fun demoVesselId(): String =
        cachedId ?: buildDemo().vessel.id.also { cachedId = it }

    private suspend fun buildDemo(): DemoVesselData =
        seedDataSource.buildDemoVessel(time.todayEpochDay(), time.nowMillis())
            .also { cachedId = it.vessel.id }

    private suspend fun installData(data: DemoVesselData) {
        database.withTransaction {
            purge(data.vessel.id)
            // The demo is what the officer just asked to see; only one vessel may be active (§5).
            vesselDao.clearActive()
            vesselDao.upsert(data.vessel.copy(isActive = true).toEntity())
            deckDao.upsertAll(data.decks.map { it.toEntity() })
            zoneDao.upsertAll(data.zones.map { it.toEntity() })
            // Parents and children live in one table with no FK between them, so one write does.
            equipmentDao.upsertAll(data.equipment.map { it.toEntity() })
            deficiencyDao.upsertAll(data.deficiencies.map { it.toEntity() })
        }
    }

    /**
     * Delete everything belonging to [vesselId] except the vessel row. Must run inside a
     * transaction. Only `decks` cascade from `vessels` (§6.2); the rest carry no foreign key, so
     * they are removed explicitly — leaving them would strand a hundred markers that no screen can
     * reach but every export would still find.
     */
    private suspend fun purge(vesselId: String) {
        for (deck in deckDao.getByVessel(vesselId)) {
            zoneDao.deleteByDeck(deck.id)
            deckDao.deleteById(deck.id)
        }
        for (id in database.idsWhere("SELECT id FROM deficiencies WHERE vesselId = ?", vesselId)) {
            deficiencyDao.deleteById(id)
        }
        for (id in database.idsWhere("SELECT id FROM equipment WHERE vesselId = ?", vesselId)) {
            for (instance in taskInstanceDao.getByEquipment(id)) {
                taskInstanceDao.deleteById(instance.id)
            }
            equipmentDao.clearCategories(id)
            equipmentDao.deletePermanently(id)
        }
    }
}

/**
 * The demo vessel of §14, as a seam the UI can depend on without pulling in the seed assets.
 *
 * Kept in `data-repository` rather than in `core-common`'s repository set because it is an
 * onboarding convenience, not part of the domain: no screen needs it to function.
 */
interface DemoVessel {
    /** Installs (or reinstalls) the demo vessel and returns its id. */
    suspend fun install(): String

    /** Removes the demo vessel and everything under it. */
    suspend fun uninstall()

    suspend fun isInstalled(): Boolean

    suspend fun demoVesselId(): String
}
