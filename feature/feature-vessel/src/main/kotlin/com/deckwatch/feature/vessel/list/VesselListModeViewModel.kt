package com.deckwatch.feature.vessel.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListModeUiState(
    val vessel: Vessel? = null,
    val groups: List<DeckGroup> = emptyList(),
    val types: Map<String, EquipmentType> = emptyMap(),
    val isLoading: Boolean = true,
) {
    val hasVessel: Boolean get() = vessel != null

    /** True once loaded when the vessel has no decks at all — the §14 first-run state. */
    val hasNoDecks: Boolean get() = !isLoading && hasVessel && groups.none { !it.isUnplaced }
}

/**
 * LIST MODE (§7.1C) for the active vessel: Deck → Zone → Equipment, fully functional with no
 * graphics and readable under TalkBack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VesselListModeViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    referenceRepository: ReferenceRepository,
) : ViewModel() {

    /** The six §6.3 presets, offered straight in the empty state (§14). */
    val presets: StateFlow<List<PlanPreset>> = referenceRepository.observePlanPresets()
        .map(BuiltInPlanPresets::merge)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BuiltInPlanPresets.all)

    val uiState: StateFlow<ListModeUiState> = vesselRepository.observeActiveVessel()
        .flatMapLatest { vessel ->
            if (vessel == null) {
                flowOf(ListModeUiState(isLoading = false))
            } else {
                vesselRepository.observeDecks(vessel.id).flatMapLatest { decks ->
                    combine(
                        zonesByDeck(decks.map { it.id }),
                        equipmentRepository.observeEquipment(vessel.id),
                        referenceRepository.observeEquipmentTypes(),
                    ) { zones, equipment, types ->
                        ListModeUiState(
                            vessel = vessel,
                            groups = ListModeGrouping.group(decks, zones, equipment),
                            types = types.associateBy { it.typeKey },
                            isLoading = false,
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ListModeUiState(),
        )

    /**
     * Creates a deck straight from a preset tapped in the empty state — the twenty-second path of
     * §6.3. The repository owns the level index; the first deck it makes becomes level 0.
     */
    fun createDeckFromPreset(preset: PlanPreset, name: String) {
        val vesselId = uiState.value.vessel?.id ?: return
        viewModelScope.launch {
            val deck = vesselRepository.addDeckAbove(
                vesselId = vesselId,
                name = name,
                shortCode = preset.suggestedShortCode,
                plan = preset.plan,
            )
            vesselRepository.upsertDeck(deck.copy(updatedAt = Dates.nowMillis()))
        }
    }

    /** `combine` of an empty list never emits, so an empty deck stack short-circuits. */
    private fun zonesByDeck(deckIds: List<String>): Flow<Map<String, List<Zone>>> =
        if (deckIds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                deckIds.map { id -> vesselRepository.observeZones(id).map { id to it } },
            ) { pairs -> pairs.toMap() }
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
