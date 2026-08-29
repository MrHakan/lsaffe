package com.deckwatch.data.repository

import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.database.TransactionRunner
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
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [VesselRepository] — MASTER_PROMPT §6.1 to §6.4.
 *
 * The DAOs already order every observation the way the UI reads it (decks highest-first for the
 * 2.5D stack, categories by sort order), so this class maps entities to models and owns the two
 * things SQL cannot do for it:
 *
 * * the `levelIndex` mechanic of §6.2; and
 * * cleaning up after a deletion. Only `decks` has a foreign key — everything else is deliberately
 *   FK-free so import merge can carry rows whose parents arrive later (§13.5) — so the cascade a
 *   schema would give us is written out here instead, inside one transaction.
 */
@Singleton
class RoomVesselRepository @Inject constructor(
    private val vesselDao: VesselDao,
    private val deckDao: DeckDao,
    private val zoneDao: ZoneDao,
    private val categoryDao: CategoryDao,
    private val equipmentDao: EquipmentDao,
    private val taskInstanceDao: TaskInstanceDao,
    private val roundDao: RoundDao,
    private val roundItemDao: RoundItemDao,
    private val deficiencyDao: DeficiencyDao,
    private val idFactory: IdFactory,
    private val clock: AppClock,
    private val transaction: TransactionRunner,
) : VesselRepository {

    override fun observeVessels(): Flow<List<Vessel>> =
        vesselDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeActiveVessel(): Flow<Vessel?> =
        vesselDao.observeActive().map { it?.toModel() }

    override suspend fun getVessel(id: String): Vessel? = vesselDao.getById(id)?.toModel()

    override suspend fun upsertVessel(vessel: Vessel) = vesselDao.upsert(vessel.toEntity())

    /**
     * Deleting a vessel takes everything recorded against it: its equipment and their task
     * history, its rounds and deficiencies, and the zones drawn on its decks. The decks themselves
     * go by foreign key. Nothing here is soft: the 10-second undo of C10 covers deleting one item,
     * while removing a whole vessel is a separately confirmed action (§7 vessel manager).
     */
    override suspend fun deleteVessel(id: String) = transaction {
        deckDao.getByVessel(id).forEach { zoneDao.deleteByDeck(it.id) }
        taskInstanceDao.deleteForVessel(id)
        equipmentDao.deleteByVessel(id)
        roundItemDao.deleteByVessel(id)
        roundDao.deleteByVessel(id)
        deficiencyDao.deleteByVessel(id)
        vesselDao.deleteById(id)
    }

    override suspend fun setActiveVessel(id: String) = vesselDao.setActive(id)

    // ------------------------------------------------------------------ decks

    override fun observeDecks(vesselId: String): Flow<List<Deck>> =
        deckDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getDeck(id: String): Deck? = deckDao.getById(id)?.toModel()

    override suspend fun upsertDeck(deck: Deck) = deckDao.upsert(deck.toEntity())

    /**
     * The deck's zones go with it; the equipment standing on it returns to the unplaced inbox
     * rather than disappearing with the deck — losing a ship's extinguishers because a deck was
     * renamed the hard way would breach C10.
     */
    override suspend fun deleteDeck(id: String) = transaction {
        equipmentDao.unplaceAllOnDeck(id, clock.nowMillis())
        zoneDao.deleteByDeck(id)
        deckDao.deleteById(id)
    }

    /** `max(levelIndex) + `[LEVEL_STEP], or [FIRST_LEVEL_INDEX] on a vessel with no decks — §6.2. */
    override suspend fun addDeckAbove(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        val level = deckDao.maxLevelIndex(vesselId)?.plus(LEVEL_STEP) ?: FIRST_LEVEL_INDEX
        return createDeck(vesselId, name, shortCode, plan, level)
    }

    /** `min(levelIndex) - `[LEVEL_STEP], or [FIRST_LEVEL_INDEX] on a vessel with no decks — §6.2. */
    override suspend fun addDeckBelow(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        val level = deckDao.minLevelIndex(vesselId)?.minus(LEVEL_STEP) ?: FIRST_LEVEL_INDEX
        return createDeck(vesselId, name, shortCode, plan, level)
    }

    /**
     * The midpoint of the two neighbours — §6.2.
     *
     * @throws IllegalArgumentException when the neighbours are adjacent, so no integer lies
     *   between them. The UI offers the affordance disabled in that case
     *   (`DeckOrdering.hasRoomBetween`), so reaching here means the stack needs renumbering.
     */
    override suspend fun insertDeckBetween(
        vesselId: String,
        lowerLevelIndex: Int,
        upperLevelIndex: Int,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        val low = minOf(lowerLevelIndex, upperLevelIndex)
        val high = maxOf(lowerLevelIndex, upperLevelIndex)
        val midpoint = (low + high).floorDiv(2)
        require(midpoint != low && midpoint != high) {
            "No room between levelIndex $low and $high — renumber the stack first."
        }
        return createDeck(vesselId, name, shortCode, plan, midpoint)
    }

    private suspend fun createDeck(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
        levelIndex: Int,
    ): Deck {
        val now = clock.nowMillis()
        val deck = Deck(
            id = idFactory.newId(),
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

    // ------------------------------------------------------------------ zones and categories

    override fun observeZones(deckId: String): Flow<List<Zone>> =
        zoneDao.observeByDeck(deckId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertZone(zone: Zone) = zoneDao.upsert(zone.toEntity())

    /** Equipment inside the zone stays where it is and simply stops belonging to a zone. */
    override suspend fun deleteZone(id: String) = transaction {
        equipmentDao.clearZoneReferences(id, clock.nowMillis())
        zoneDao.deleteById(id)
    }

    /** Global categories (`vesselId IS NULL`) come back alongside the vessel's own — §6.4. */
    override fun observeCategories(vesselId: String?): Flow<List<Category>> =
        categoryDao.observeForVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertCategory(category: Category) = categoryDao.upsert(category.toEntity())

    override suspend fun deleteCategory(id: String) = categoryDao.deleteById(id)

    companion object {
        /** The first deck created on a vessel is the "ground" — §6.2. */
        const val FIRST_LEVEL_INDEX: Int = 0

        /** Step of 10 leaves room to insert between decks without a renumbering migration — §6.2. */
        const val LEVEL_STEP: Int = 10
    }
}
