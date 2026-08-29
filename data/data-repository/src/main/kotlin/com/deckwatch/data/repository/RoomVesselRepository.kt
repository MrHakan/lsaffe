package com.deckwatch.data.repository

import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.database.dao.CategoryDao
import com.deckwatch.core.database.dao.DeckDao
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
 * 2.5D stack, categories by sort order), so this class only maps entities to models and owns the
 * one piece of logic SQL cannot express: the `levelIndex` mechanic of §6.2.
 */
@Singleton
class RoomVesselRepository @Inject constructor(
    private val vesselDao: VesselDao,
    private val deckDao: DeckDao,
    private val zoneDao: ZoneDao,
    private val categoryDao: CategoryDao,
    private val idFactory: IdFactory,
    private val clock: AppClock,
) : VesselRepository {

    override fun observeVessels(): Flow<List<Vessel>> =
        vesselDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeActiveVessel(): Flow<Vessel?> =
        vesselDao.observeActive().map { it?.toModel() }

    override suspend fun getVessel(id: String): Vessel? = vesselDao.getById(id)?.toModel()

    override suspend fun upsertVessel(vessel: Vessel) = vesselDao.upsert(vessel.toEntity())

    override suspend fun deleteVessel(id: String) = vesselDao.deleteById(id)

    override suspend fun setActiveVessel(id: String) = vesselDao.setActive(id)

    // ------------------------------------------------------------------ decks

    override fun observeDecks(vesselId: String): Flow<List<Deck>> =
        deckDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getDeck(id: String): Deck? = deckDao.getById(id)?.toModel()

    override suspend fun upsertDeck(deck: Deck) = deckDao.upsert(deck.toEntity())

    override suspend fun deleteDeck(id: String) = deckDao.deleteById(id)

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

    override suspend fun deleteZone(id: String) = zoneDao.deleteById(id)

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
