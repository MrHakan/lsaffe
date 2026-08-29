package com.deckwatch.core.common.repository

import com.deckwatch.core.model.Category
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.UserNote
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import kotlinx.coroutines.flow.Flow

/** Vessels, decks, zones, logical categories. */
interface VesselRepository {
    fun observeVessels(): Flow<List<Vessel>>
    fun observeActiveVessel(): Flow<Vessel?>
    suspend fun getVessel(id: String): Vessel?
    suspend fun upsertVessel(vessel: Vessel)
    suspend fun deleteVessel(id: String)
    suspend fun setActiveVessel(id: String)

    fun observeDecks(vesselId: String): Flow<List<Deck>>
    suspend fun getDeck(id: String): Deck?
    suspend fun upsertDeck(deck: Deck)
    suspend fun deleteDeck(id: String)

    /** levelIndex mechanic — §6.2. Returns the created deck. */
    suspend fun addDeckAbove(vesselId: String, name: String, shortCode: String?, plan: DeckPlan): Deck
    suspend fun addDeckBelow(vesselId: String, name: String, shortCode: String?, plan: DeckPlan): Deck
    suspend fun insertDeckBetween(
        vesselId: String,
        lowerLevelIndex: Int,
        upperLevelIndex: Int,
        name: String,
        shortCode: String?,
        plan: DeckPlan,
    ): Deck

    fun observeZones(deckId: String): Flow<List<Zone>>
    suspend fun upsertZone(zone: Zone)
    suspend fun deleteZone(id: String)

    fun observeCategories(vesselId: String?): Flow<List<Category>>
    suspend fun upsertCategory(category: Category)
    suspend fun deleteCategory(id: String)
}

/** Equipment records and their dynamic attributes. */
interface EquipmentRepository {
    fun observeEquipment(vesselId: String): Flow<List<Equipment>>
    fun observeEquipmentOnDeck(deckId: String): Flow<List<Equipment>>
    fun observeChildren(parentId: String): Flow<List<Equipment>>
    fun observeUnplaced(vesselId: String): Flow<List<Equipment>>
    suspend fun getEquipment(id: String): Equipment?
    suspend fun upsertEquipment(equipment: Equipment)
    suspend fun setCondition(id: String, grade: ConditionGrade, atMillis: Long)
    suspend fun move(id: String, deckId: String?, zoneId: String?, posX: Float, posY: Float)
    /** Soft delete (sets deletedAt) — undoable for 10 s. */
    suspend fun softDelete(id: String, atMillis: Long)
    suspend fun undelete(id: String)
    /** Duplicate ×N with auto-incremented tags — §7.5. Returns new ids. */
    suspend fun duplicate(id: String, count: Int): List<String>
    suspend fun setCategories(equipmentId: String, categoryIds: List<String>)
    fun observeCategoryIds(equipmentId: String): Flow<List<String>>
    /** Next free numeric suffix for a tag prefix on a deck, e.g. FE-UD-{n}. */
    suspend fun nextTagNumber(vesselId: String, prefix: String): Int
}

/** Task definitions, task instances and the due engine's persistence. */
interface MaintenanceRepository {
    fun observeTaskDefinitions(): Flow<List<TaskDefinition>>
    suspend fun getTaskDefinition(key: String): TaskDefinition?
    suspend fun upsertTaskDefinition(definition: TaskDefinition)

    fun observeTaskInstances(equipmentId: String): Flow<List<TaskInstance>>
    fun observeOpenInstancesForVessel(vesselId: String): Flow<List<TaskInstance>>
    suspend fun upsertInstances(instances: List<TaskInstance>)
    suspend fun completeTask(
        instanceId: String,
        completedDate: Long,
        completedBy: String?,
        serviceProvider: String?,
        certificateNumber: String?,
        findings: String?,
        conditionAfter: ConditionGrade?,
    )

    /** Re-derive and persist due state for one equipment item (in-transaction). */
    suspend fun recomputeDue(equipmentId: String)

    /** Re-derive and persist due state for a whole vessel. */
    suspend fun recomputeDueForVessel(vesselId: String)
}

/** Rounds, round items and deficiencies. */
interface InspectionRepository {
    fun observeRounds(vesselId: String): Flow<List<Round>>
    suspend fun getRound(id: String): Round?
    suspend fun upsertRound(round: Round)
    fun observeRoundItems(roundId: String): Flow<List<RoundItem>>
    suspend fun upsertRoundItem(item: RoundItem)

    fun observeDeficiencies(vesselId: String): Flow<List<Deficiency>>
    fun observeOpenDeficiencies(vesselId: String): Flow<List<Deficiency>>
    suspend fun getDeficiency(id: String): Deficiency?
    suspend fun upsertDeficiency(deficiency: Deficiency)
}

/** Bundled reference content: regulation cards, catalogue, templates, presets, symbols, user notes. */
interface ReferenceRepository {
    fun observeRegulationCards(): Flow<List<RegulationCard>>
    suspend fun getRegulationCard(refKey: String): RegulationCard?
    fun searchRegulationCards(query: String): Flow<List<RegulationCard>>

    fun observeEquipmentTypes(): Flow<List<EquipmentType>>
    suspend fun getEquipmentType(typeKey: String): EquipmentType?
    suspend fun upsertUserDefinedType(type: EquipmentType)

    fun observeRoundTemplates(): Flow<List<RoundTemplate>>
    fun observePlanPresets(): Flow<List<PlanPreset>>
    fun observeSymbols(): Flow<List<SymbolInfo>>

    fun observeUserNotes(): Flow<List<UserNote>>
    suspend fun upsertUserNote(note: UserNote)
    suspend fun deleteUserNote(id: String)
}
