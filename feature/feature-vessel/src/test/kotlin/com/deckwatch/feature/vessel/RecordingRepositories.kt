package com.deckwatch.feature.vessel

import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import com.deckwatch.core.testing.FakeEquipmentRepository
import com.deckwatch.core.testing.FakeVesselRepository
import kotlinx.coroutines.flow.Flow

/**
 * Decorators that delegate to the shared fakes while appending every mutating call to a common
 * [log]. Some rules in this feature are about *ordering*, not just about the end state — deleting
 * a deck has to unplace its equipment first (C10) — and only a call log can prove that.
 */
class CallLog {
    private val entries = mutableListOf<String>()

    fun record(entry: String) {
        entries += entry
    }

    fun all(): List<String> = entries.toList()

    fun indexOfFirst(prefix: String): Int = entries.indexOfFirst { it.startsWith(prefix) }
}

class RecordingVesselRepository(
    private val delegate: FakeVesselRepository,
    private val log: CallLog,
) : VesselRepository by delegate {

    override suspend fun deleteDeck(id: String) {
        log.record("deleteDeck:$id")
        delegate.deleteDeck(id)
    }

    override suspend fun deleteVessel(id: String) {
        log.record("deleteVessel:$id")
        delegate.deleteVessel(id)
    }

    override suspend fun upsertDeck(deck: Deck) {
        log.record("upsertDeck:${deck.id}")
        delegate.upsertDeck(deck)
    }

    override suspend fun upsertZone(zone: Zone) {
        log.record("upsertZone:${zone.id}:${zone.sortOrder}")
        delegate.upsertZone(zone)
    }

    override suspend fun upsertCategory(category: Category) {
        log.record("upsertCategory:${category.id}")
        delegate.upsertCategory(category)
    }

    override suspend fun upsertVessel(vessel: Vessel) {
        log.record("upsertVessel:${vessel.id}")
        delegate.upsertVessel(vessel)
    }

    override suspend fun setActiveVessel(id: String) {
        log.record("setActiveVessel:$id")
        delegate.setActiveVessel(id)
    }

    override suspend fun addDeckAbove(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        log.record("addDeckAbove:$name")
        return delegate.addDeckAbove(vesselId, name, shortCode, plan)
    }

    override suspend fun addDeckBelow(
        vesselId: String,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        log.record("addDeckBelow:$name")
        return delegate.addDeckBelow(vesselId, name, shortCode, plan)
    }

    override suspend fun insertDeckBetween(
        vesselId: String,
        lowerLevelIndex: Int,
        upperLevelIndex: Int,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck {
        log.record("insertDeckBetween:$lowerLevelIndex:$upperLevelIndex")
        return delegate.insertDeckBetween(vesselId, lowerLevelIndex, upperLevelIndex, name, shortCode, plan)
    }
}

class RecordingEquipmentRepository(
    private val delegate: FakeEquipmentRepository,
    private val log: CallLog,
) : EquipmentRepository by delegate {

    override suspend fun move(id: String, deckId: String?, zoneId: String?, posX: Float, posY: Float) {
        log.record("move:$id:${deckId ?: "unplaced"}")
        delegate.move(id, deckId, zoneId, posX, posY)
    }

    override suspend fun setCondition(id: String, grade: ConditionGrade, atMillis: Long) {
        log.record("setCondition:$id:$grade")
        delegate.setCondition(id, grade, atMillis)
    }

    override fun observeEquipmentOnDeck(deckId: String): Flow<List<Equipment>> =
        delegate.observeEquipmentOnDeck(deckId)
}
