package com.deckwatch.core.testing

import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [VesselRepository] backed by [MutableStateFlow] maps.
 *
 * Implements the `levelIndex` mechanic of §6.2 exactly:
 * * the first deck created on a vessel gets `levelIndex = 0`;
 * * "add above" gets `max(levelIndex) + `[LEVEL_STEP];
 * * "add below" gets `min(levelIndex) - `[LEVEL_STEP];
 * * "insert between" gets the midpoint of the two neighbours.
 *
 * The step of 10 is what leaves room to insert without a renumbering migration.
 *
 * @param idFactory override to make generated ids deterministic in a test.
 * @param clock supplies `createdAt` / `updatedAt` epoch-millis.
 */
class FakeVesselRepository(
    private val idFactory: () -> String = ::randomId,
    private val clock: () -> Long = Dates::nowMillis,
) : VesselRepository {

    val vessels = MutableStateFlow<Map<String, Vessel>>(emptyMap())
    val decks = MutableStateFlow<Map<String, Deck>>(emptyMap())
    val zones = MutableStateFlow<Map<String, Zone>>(emptyMap())
    val categories = MutableStateFlow<Map<String, Category>>(emptyMap())

    // ------------------------------------------------------------------ vessels

    override fun observeVessels(): Flow<List<Vessel>> =
        vessels.map { current -> current.values.sortedBy { it.name } }

    override fun observeActiveVessel(): Flow<Vessel?> =
        vessels.map { current -> current.values.firstOrNull { it.isActive } }

    override suspend fun getVessel(id: String): Vessel? = vessels.value[id]

    override suspend fun upsertVessel(vessel: Vessel) {
        vessels.update { it + (vessel.id to vessel) }
    }

    override suspend fun deleteVessel(id: String) {
        vessels.update { it - id }
        val orphanDecks = decks.value.values.filter { it.vesselId == id }.map { it.id }.toSet()
        decks.update { current -> current - orphanDecks }
        zones.update { current -> current.filterValues { it.deckId !in orphanDecks } }
        categories.update { current -> current.filterValues { it.vesselId != id } }
    }

    /** Exactly one vessel is active at a time — §5. */
    override suspend fun setActiveVessel(id: String) {
        vessels.update { current ->
            current.mapValues { (key, vessel) -> vessel.copy(isActive = key == id) }
        }
    }

    // ------------------------------------------------------------------ decks

    /** Sorted by `levelIndex` descending — the stack renders the highest deck at the top (§6.2). */
    override fun observeDecks(vesselId: String): Flow<List<Deck>> =
        decks.map { current ->
            current.values.filter { it.vesselId == vesselId }.sortedByDescending { it.levelIndex }
        }

    override suspend fun getDeck(id: String): Deck? = decks.value[id]

    override suspend fun upsertDeck(deck: Deck) {
        decks.update { it + (deck.id to deck) }
    }

    override suspend fun deleteDeck(id: String) {
        decks.update { it - id }
        zones.update { current -> current.filterValues { it.deckId != id } }
    }

    override suspend fun addDeckAbove(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        val existing = levelsOf(vesselId)
        val level = existing.maxOrNull()?.plus(LEVEL_STEP) ?: FIRST_LEVEL_INDEX
        return createDeck(vesselId, name, shortCode, plan, level)
    }

    override suspend fun addDeckBelow(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        val existing = levelsOf(vesselId)
        val level = existing.minOrNull()?.minus(LEVEL_STEP) ?: FIRST_LEVEL_INDEX
        return createDeck(vesselId, name, shortCode, plan, level)
    }

    /**
     * The midpoint of the two neighbours — §6.2.
     *
     * @throws IllegalArgumentException when the two levels are adjacent, so there is no integer
     *   between them. Real callers renumber the stack; a fake makes the condition loud instead.
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
        val midpoint = Math.floorDiv(low + high, 2)
        require(midpoint != low && midpoint != high) {
            "No room between levelIndex $low and $high — renumber the stack first."
        }
        return createDeck(vesselId, name, shortCode, plan, midpoint)
    }

    private fun levelsOf(vesselId: String): List<Int> =
        decks.value.values.filter { it.vesselId == vesselId }.map { it.levelIndex }

    private fun createDeck(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
        levelIndex: Int,
    ): Deck {
        val now = clock()
        val deck = Deck(
            id = idFactory(),
            vesselId = vesselId,
            name = name,
            shortCode = shortCode,
            levelIndex = levelIndex,
            plan = plan,
            createdAt = now,
            updatedAt = now,
        )
        decks.update { it + (deck.id to deck) }
        return deck
    }

    // ------------------------------------------------------------------ zones and categories

    override fun observeZones(deckId: String): Flow<List<Zone>> =
        zones.map { current ->
            current.values.filter { it.deckId == deckId }.sortedBy { it.sortOrder }
        }

    override suspend fun upsertZone(zone: Zone) {
        zones.update { it + (zone.id to zone) }
    }

    override suspend fun deleteZone(id: String) {
        zones.update { it - id }
    }

    /**
     * A category with a null `vesselId` is global and available on every vessel — §6.4. Asking for
     * a specific vessel therefore returns that vessel's own categories *and* the global ones.
     */
    override fun observeCategories(vesselId: String?): Flow<List<Category>> =
        categories.map { current ->
            current.values
                .filter { it.vesselId == null || it.vesselId == vesselId }
                .sortedBy { it.sortOrder }
        }

    override suspend fun upsertCategory(category: Category) {
        categories.update { it + (category.id to category) }
    }

    override suspend fun deleteCategory(id: String) {
        categories.update { it - id }
    }

    companion object {
        /** First deck created is the "ground" — §6.2. */
        const val FIRST_LEVEL_INDEX: Int = 0

        /** Step of 10 leaves room to insert without a renumber migration — §6.2. */
        const val LEVEL_STEP: Int = 10
    }
}
