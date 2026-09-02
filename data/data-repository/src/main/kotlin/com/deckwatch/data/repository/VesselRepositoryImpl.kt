package com.deckwatch.data.repository

import androidx.room.withTransaction
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.dao.CategoryDao
import com.deckwatch.core.database.dao.DeckDao
import com.deckwatch.core.database.dao.DeficiencyDao
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.dao.RoundDao
import com.deckwatch.core.database.dao.RoundItemDao
import com.deckwatch.core.database.dao.TaskInstanceDao
import com.deckwatch.core.database.dao.VesselDao
import com.deckwatch.core.database.dao.ZoneDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Vessels, decks, zones and logical categories — MASTER_PROMPT §6.1, §6.2, §6.4.
 *
 * **Active vessel — one source of truth.** The authority is the `vessels.isActive` column, because
 * it is the value that participates in the same transaction as the rest of a switch and survives a
 * database restore. `UserPreferences.activeVesselId` is a *mirror* kept in step by
 * [setActiveVessel], [upsertVessel] and [deleteVessel] so that a caller which only has the
 * settings store (the daily worker, a cold start before the database opens) still knows which
 * vessel was last selected. [observeActiveVessel] reads the column first and falls back to the
 * mirror only when no row is flagged — never the other way round, and never by writing while
 * something is merely observing.
 *
 * **Deleting a vessel** purges its contents in one transaction. Only `decks` cascade by foreign
 * key (§6.2); equipment, zones, task instances and deficiencies carry no FK, so leaving them would
 * strand rows that no screen can reach but every export would still find. Deleting a vessel is an
 * explicit, confirmed user action, so the purge is deliberate — not a silent loss (C10).
 */
@Singleton
class VesselRepositoryImpl @Inject constructor(
    private val database: DeckWatchDatabase,
    private val vesselDao: VesselDao,
    private val deckDao: DeckDao,
    private val zoneDao: ZoneDao,
    private val categoryDao: CategoryDao,
    private val equipmentDao: EquipmentDao,
    private val taskInstanceDao: TaskInstanceDao,
    private val deficiencyDao: DeficiencyDao,
    private val roundDao: RoundDao,
    private val roundItemDao: RoundItemDao,
    private val preferences: UserPreferencesRepository,
    private val dispatchers: DispatcherProvider,
    private val time: TimeSource,
) : VesselRepository {

    override fun observeVessels(): Flow<List<Vessel>> =
        vesselDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeActiveVessel(): Flow<Vessel?> = combine(
        vesselDao.observeAll(),
        preferences.userPreferences.map { it.activeVesselId }.distinctUntilChanged(),
    ) { vessels, mirroredId ->
        vessels.firstOrNull { it.isActive } ?: vessels.firstOrNull { it.id == mirroredId }
    }.map { it?.toModel() }.distinctUntilChanged()

    override suspend fun getVessel(id: String): Vessel? = withContext(dispatchers.io) {
        vesselDao.getById(id)?.toModel()
    }

    /**
     * Upserting a vessel with `isActive = true` clears the flag everywhere else in the same
     * transaction, so the app can never observe two active vessels (§5).
     */
    override suspend fun upsertVessel(vessel: Vessel) = withContext(dispatchers.io) {
        database.withTransaction {
            if (vessel.isActive) vesselDao.clearActive()
            vesselDao.upsert(vessel.toEntity())
        }
        if (vessel.isActive) preferences.setActiveVesselId(vessel.id)
    }

    override suspend fun deleteVessel(id: String) = withContext(dispatchers.io) {
        database.withTransaction {
            purgeVesselContents(id)
            vesselDao.deleteById(id)
        }
        if (preferences.get().activeVesselId == id) {
            // Promote whatever the database still flags, otherwise clear the mirror.
            preferences.setActiveVesselId(vesselDao.getActive()?.id)
        }
    }

    override suspend fun setActiveVessel(id: String) = withContext(dispatchers.io) {
        vesselDao.setActive(id)
        preferences.setActiveVesselId(id)
    }

    // ---- decks -----------------------------------------------------------------------------

    override fun observeDecks(vesselId: String): Flow<List<Deck>> =
        deckDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getDeck(id: String): Deck? = withContext(dispatchers.io) {
        deckDao.getById(id)?.toModel()
    }

    override suspend fun upsertDeck(deck: Deck) = withContext(dispatchers.io) {
        deckDao.upsert(deck.toEntity())
    }

    /** Zones belong to the deck and have no foreign key of their own, so they go with it. */
    override suspend fun deleteDeck(id: String) = withContext(dispatchers.io) {
        database.withTransaction {
            zoneDao.deleteByDeck(id)
            deckDao.deleteById(id)
        }
    }

    /**
     * "Add deck above" — the first deck of a vessel is level 0, otherwise `max + 10` (§6.2). The
     * step of 10 is what leaves room for [insertDeckBetween] without a renumbering migration.
     */
    override suspend fun addDeckAbove(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck = withContext(dispatchers.io) {
        database.withTransaction {
            val level = deckDao.maxLevelIndex(vesselId)?.let { it + LEVEL_STEP } ?: FIRST_LEVEL
            insertDeck(vesselId, name, shortCode, plan, level)
        }
    }

    /** "Add deck below" — `min - 10`, or level 0 when this is the vessel's first deck (§6.2). */
    override suspend fun addDeckBelow(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck = withContext(dispatchers.io) {
        database.withTransaction {
            val level = deckDao.minLevelIndex(vesselId)?.let { it - LEVEL_STEP } ?: FIRST_LEVEL
            insertDeck(vesselId, name, shortCode, plan, level)
        }
    }

    /**
     * "Insert between" — the midpoint of the two neighbouring levels (§6.2).
     *
     * The midpoint uses floor division so a negative pair behaves like a positive one. When the two
     * neighbours are adjacent (or inverted, or equal) there is no integer between them and the
     * insert cannot be honoured: rather than silently placing the deck on top of an existing level
     * — which the unique `(vesselId, levelIndex)` index would reject anyway — this throws an
     * [IllegalStateException] naming both levels, so the UI can tell the officer to renumber.
     */
    override suspend fun insertDeckBetween(
        vesselId: String,
        lowerLevelIndex: Int,
        upperLevelIndex: Int,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck = withContext(dispatchers.io) {
        val midpoint = Math.floorDiv(lowerLevelIndex + upperLevelIndex, 2)
        check(lowerLevelIndex < upperLevelIndex && midpoint != lowerLevelIndex && midpoint != upperLevelIndex) {
            "Cannot insert a deck between levelIndex $lowerLevelIndex and $upperLevelIndex: " +
                "the levels are adjacent, so there is no level between them. " +
                "Renumber the stack (levels are spaced by $LEVEL_STEP) and try again."
        }
        database.withTransaction {
            insertDeck(vesselId, name, shortCode, plan, midpoint)
        }
    }

    private suspend fun insertDeck(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
        levelIndex: Int,
    ): Deck {
        val now = time.nowMillis()
        val deck = Deck(
            id = UUID.randomUUID().toString(),
            vesselId = vesselId,
            name = name,
            shortCode = shortCode,
            levelIndex = levelIndex,
            plan = plan,
            createdAt = now,
            updatedAt = now,
        )
        deckDao.insert(deck.toEntity())
        return deck
    }

    // ---- zones and categories --------------------------------------------------------------

    override fun observeZones(deckId: String): Flow<List<Zone>> =
        zoneDao.observeByDeck(deckId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertZone(zone: Zone) = withContext(dispatchers.io) {
        zoneDao.upsert(zone.toEntity())
    }

    override suspend fun deleteZone(id: String) = withContext(dispatchers.io) {
        zoneDao.deleteById(id)
    }

    override fun observeCategories(vesselId: String?): Flow<List<Category>> =
        categoryDao.observeForVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertCategory(category: Category) = withContext(dispatchers.io) {
        categoryDao.upsert(category.toEntity())
    }

    override suspend fun deleteCategory(id: String) = withContext(dispatchers.io) {
        categoryDao.deleteById(id)
    }

    /**
     * Remove everything that hangs off a vessel but is not reached by a foreign key. Must be called
     * inside a transaction; `decks` are left to the FK cascade on `vessels`.
     */
    private suspend fun purgeVesselContents(vesselId: String) {
        for (deck in deckDao.getByVessel(vesselId)) {
            zoneDao.deleteByDeck(deck.id)
        }
        for (roundId in database.idsWhere("SELECT id FROM rounds WHERE vesselId = ?", vesselId)) {
            roundItemDao.deleteByRound(roundId)
            roundDao.deleteById(roundId)
        }
        for (id in database.idsWhere("SELECT id FROM deficiencies WHERE vesselId = ?", vesselId)) {
            deficiencyDao.deleteById(id)
        }
        // Soft-deleted rows are purged too: the vessel itself is going.
        for (id in database.idsWhere("SELECT id FROM equipment WHERE vesselId = ?", vesselId)) {
            for (instance in taskInstanceDao.getByEquipment(id)) {
                taskInstanceDao.deleteById(instance.id)
            }
            equipmentDao.clearCategories(id)
            equipmentDao.deletePermanently(id)
        }
    }

    private companion object {
        /** The first deck a user creates is the vessel's "ground" — §6.2. */
        const val FIRST_LEVEL = 0

        /** Levels are spaced by 10 so a deck can be inserted between two others — §6.2. */
        const val LEVEL_STEP = 10
    }
}
