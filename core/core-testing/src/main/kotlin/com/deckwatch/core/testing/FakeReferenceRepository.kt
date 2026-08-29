package com.deckwatch.core.testing

import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.SymbolInfo
import com.deckwatch.core.model.UserNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ReferenceRepository] standing in for the bundled seed content of §19: regulation
 * cards, the equipment catalogue, round templates, deck-plan presets, the symbol library and the
 * user's own notes.
 */
class FakeReferenceRepository : ReferenceRepository {

    val regulationCards = MutableStateFlow<Map<String, RegulationCard>>(emptyMap())
    val equipmentTypes = MutableStateFlow<Map<String, EquipmentType>>(emptyMap())
    val roundTemplates = MutableStateFlow<Map<String, RoundTemplate>>(emptyMap())
    val planPresets = MutableStateFlow<Map<String, PlanPreset>>(emptyMap())
    val symbols = MutableStateFlow<Map<String, SymbolInfo>>(emptyMap())
    val userNotes = MutableStateFlow<Map<String, UserNote>>(emptyMap())

    // ------------------------------------------------------------------ seeding helpers

    /** Seed a bundled catalogue entry as-is — unlike [upsertUserDefinedType] this keeps `isUserDefined`. */
    fun seedEquipmentType(type: EquipmentType) {
        equipmentTypes.update { it + (type.typeKey to type) }
    }

    fun seedRegulationCard(card: RegulationCard) {
        regulationCards.update { it + (card.refKey to card) }
    }

    fun seedRoundTemplate(template: RoundTemplate) {
        roundTemplates.update { it + (template.key to template) }
    }

    fun seedPlanPreset(preset: PlanPreset) {
        planPresets.update { it + (preset.key to preset) }
    }

    fun seedSymbol(symbol: SymbolInfo) {
        symbols.update { it + (symbol.key to symbol) }
    }

    // ------------------------------------------------------------------ ReferenceRepository

    override fun observeRegulationCards(): Flow<List<RegulationCard>> =
        regulationCards.map { current -> current.values.sortedBy { it.citation } }

    override suspend fun getRegulationCard(refKey: String): RegulationCard? =
        regulationCards.value[refKey]

    /**
     * Case-insensitive substring match over the fields the Notes tab search box covers: the
     * citation badge, the title and the WHAT statement — §8.2. A blank query returns everything.
     */
    override fun searchRegulationCards(query: String): Flow<List<RegulationCard>> =
        observeRegulationCards().map { cards ->
            val needle = query.trim()
            if (needle.isEmpty()) {
                cards
            } else {
                cards.filter { card ->
                    card.citation.contains(needle, ignoreCase = true) ||
                        card.title.contains(needle, ignoreCase = true) ||
                        card.what.contains(needle, ignoreCase = true)
                }
            }
        }

    override fun observeEquipmentTypes(): Flow<List<EquipmentType>> =
        equipmentTypes.map { current -> current.values.sortedBy { it.nameEn } }

    override suspend fun getEquipmentType(typeKey: String): EquipmentType? =
        equipmentTypes.value[typeKey]

    /** The mandatory user-defined-type escape hatch — §9.2. */
    override suspend fun upsertUserDefinedType(type: EquipmentType) {
        equipmentTypes.update { it + (type.typeKey to type.copy(isUserDefined = true)) }
    }

    override fun observeRoundTemplates(): Flow<List<RoundTemplate>> =
        roundTemplates.map { current -> current.values.sortedBy { it.key } }

    override fun observePlanPresets(): Flow<List<PlanPreset>> =
        planPresets.map { current -> current.values.sortedBy { it.key } }

    override fun observeSymbols(): Flow<List<SymbolInfo>> =
        symbols.map { current -> current.values.sortedBy { it.key } }

    override fun observeUserNotes(): Flow<List<UserNote>> =
        userNotes.map { current -> current.values.sortedByDescending { it.updatedAt } }

    override suspend fun upsertUserNote(note: UserNote) {
        userNotes.update { it + (note.id to note) }
    }

    override suspend fun deleteUserNote(id: String) {
        userNotes.update { it - id }
    }
}
