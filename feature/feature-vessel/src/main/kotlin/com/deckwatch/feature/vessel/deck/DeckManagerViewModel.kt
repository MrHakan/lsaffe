package com.deckwatch.feature.vessel.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One deck as the manager list needs it. */
data class DeckRow(
    val deck: Deck,
    val equipmentCount: Int,
    val worstCondition: ConditionGrade?,
) {
    val id: String get() = deck.id
    val levelIndex: Int get() = deck.levelIndex
}

data class DeckManagerUiState(
    val vessel: Vessel? = null,
    val decks: List<DeckRow> = emptyList(),
    val insertSlots: List<InsertSlot> = emptyList(),
    val isLoading: Boolean = true,
) {
    val hasVessel: Boolean get() = vessel != null
    val isEmpty: Boolean get() = !isLoading && hasVessel && decks.isEmpty()
}

/** What the deck edit sheet is being opened for. */
sealed interface DeckSheetTarget {
    data object AddAbove : DeckSheetTarget
    data object AddBelow : DeckSheetTarget
    data class InsertBetween(val lowerLevelIndex: Int, val upperLevelIndex: Int) : DeckSheetTarget
    data class Edit(val deck: Deck) : DeckSheetTarget
}

/** The fields the sheet collects. The `levelIndex` is never among them — the repository owns it. */
data class DeckDraft(
    val name: String,
    val shortCode: String?,
    val plan: DeckPlan,
    val colorTint: Int?,
    val notes: String?,
)

/**
 * The deck stack as a list (§7.1C) with the insert-above / insert-below / insert-between
 * affordances of §6.2.
 *
 * The `levelIndex` arithmetic deliberately lives in [VesselRepository] — this view model only
 * names the neighbours to insert between and lets the repository pick the index.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeckManagerViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    referenceRepository: ReferenceRepository,
) : ViewModel() {

    private val requestedVesselId = MutableStateFlow<String?>(null)
    private val sheetTarget = MutableStateFlow<DeckSheetTarget?>(null)
    private val pendingDelete = MutableStateFlow<String?>(null)

    /** Open sheet, if any. */
    val sheet: StateFlow<DeckSheetTarget?> = sheetTarget

    /** Deck queued for the delete confirmation. */
    val deleteTarget: StateFlow<String?> = pendingDelete

    /** Repository presets, backfilled with the six built-ins so the picker is never empty. */
    val presets: StateFlow<List<PlanPreset>> = referenceRepository.observePlanPresets()
        .map(BuiltInPlanPresets::merge)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BuiltInPlanPresets.all)

    private val resolvedVessel: Flow<Vessel?> = combine(
        requestedVesselId,
        vesselRepository.observeVessels(),
        vesselRepository.observeActiveVessel(),
    ) { requested, all, active ->
        if (requested == null) active else all.firstOrNull { it.id == requested }
    }

    val uiState: StateFlow<DeckManagerUiState> = resolvedVessel
        .flatMapLatest { vessel ->
            if (vessel == null) {
                flowOf(DeckManagerUiState(isLoading = false))
            } else {
                combine(
                    vesselRepository.observeDecks(vessel.id),
                    equipmentRepository.observeEquipment(vessel.id),
                ) { decks, equipment -> buildDeckManagerState(vessel, decks, equipment) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DeckManagerUiState(),
        )

    /** `null` resolves to the active vessel (§5). Safe to call from composition. */
    fun bind(vesselId: String?) {
        requestedVesselId.value = vesselId
    }

    // ------------------------------------------------------------------ sheet

    fun openAddAbove() {
        sheetTarget.value = DeckSheetTarget.AddAbove
    }

    fun openAddBelow() {
        sheetTarget.value = DeckSheetTarget.AddBelow
    }

    fun openInsertBetween(slot: InsertSlot) {
        if (!slot.enabled) return
        sheetTarget.value = DeckSheetTarget.InsertBetween(slot.lowerLevelIndex, slot.upperLevelIndex)
    }

    fun openEdit(deck: Deck) {
        sheetTarget.value = DeckSheetTarget.Edit(deck)
    }

    fun closeSheet() {
        sheetTarget.value = null
    }

    /**
     * Applies [draft] for the currently open sheet target. Creation goes through the repository's
     * `addDeckAbove` / `addDeckBelow` / `insertDeckBetween` so the level index stays theirs; the
     * tint and notes are written straight afterwards because those entry points do not carry them.
     */
    fun saveDraft(draft: DeckDraft) {
        val target = sheetTarget.value ?: return
        sheetTarget.value = null
        viewModelScope.launch {
            val vesselId = resolveVesselId() ?: return@launch
            val created = when (target) {
                DeckSheetTarget.AddAbove ->
                    vesselRepository.addDeckAbove(vesselId, draft.name, draft.shortCode, draft.plan)

                DeckSheetTarget.AddBelow ->
                    vesselRepository.addDeckBelow(vesselId, draft.name, draft.shortCode, draft.plan)

                is DeckSheetTarget.InsertBetween -> vesselRepository.insertDeckBetween(
                    vesselId = vesselId,
                    lowerLevelIndex = target.lowerLevelIndex,
                    upperLevelIndex = target.upperLevelIndex,
                    name = draft.name,
                    shortCode = draft.shortCode,
                    plan = draft.plan,
                )

                is DeckSheetTarget.Edit -> target.deck
            }
            vesselRepository.upsertDeck(
                created.copy(
                    name = draft.name,
                    shortCode = draft.shortCode,
                    plan = draft.plan,
                    colorTint = draft.colorTint,
                    notes = draft.notes,
                    updatedAt = Dates.nowMillis(),
                ),
            )
        }
    }

    // ------------------------------------------------------------------ delete

    fun askDeleteDeck(deckId: String) {
        pendingDelete.value = deckId
    }

    fun cancelDeleteDeck() {
        pendingDelete.value = null
    }

    /**
     * Unplaces every equipment record on the deck **before** removing the deck, so a delete never
     * takes equipment with it — C10, no silent loss. The records land in the unplaced inbox
     * (`deckId == null`, §6.5) with their plan coordinates preserved for when they are re-placed.
     */
    fun confirmDeleteDeck(deckId: String) {
        pendingDelete.value = null
        viewModelScope.launch {
            val onDeck = equipmentRepository.observeEquipmentOnDeck(deckId).first()
            for (item in onDeck) {
                equipmentRepository.move(
                    id = item.id,
                    deckId = null,
                    zoneId = null,
                    posX = item.posX,
                    posY = item.posY,
                )
            }
            vesselRepository.deleteDeck(deckId)
        }
    }

    /**
     * The explicitly bound vessel, or the active one (§5). Resolved from the repository rather
     * than from [uiState], so writes work even when nothing is collecting the state yet.
     */
    private suspend fun resolveVesselId(): String? =
        requestedVesselId.value ?: vesselRepository.observeActiveVessel().first()?.id

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Pure projection of repository state onto the list — kept out of the view model so it is testable. */
internal fun buildDeckManagerState(
    vessel: Vessel,
    decks: List<Deck>,
    equipment: List<Equipment>,
): DeckManagerUiState {
    val byDeck = equipment.groupBy { it.deckId }
    val rows = DeckOrdering.sortedForStack(decks).map { deck ->
        val items = byDeck[deck.id].orEmpty()
        DeckRow(
            deck = deck,
            equipmentCount = items.size,
            worstCondition = DeckOrdering.worstCondition(items),
        )
    }
    return DeckManagerUiState(
        vessel = vessel,
        decks = rows,
        insertSlots = DeckOrdering.insertSlots(decks),
        isLoading = false,
    )
}
