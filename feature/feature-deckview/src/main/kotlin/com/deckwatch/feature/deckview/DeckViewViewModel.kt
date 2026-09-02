package com.deckwatch.feature.deckview

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.deckview.geometry.IsoProjection
import com.deckwatch.feature.deckview.geometry.Polygons
import com.deckwatch.feature.deckview.geometry.Vec2
import com.deckwatch.feature.deckview.model.DeckNode
import com.deckwatch.feature.deckview.model.RenderModelAssembler
import com.deckwatch.feature.deckview.model.StackRenderModel
import com.deckwatch.feature.deckview.model.SweepOrder
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** The three view modes of §7.1. */
enum class DeckViewMode { STACK, DECK, LIST }

/** A sweep in progress — §7.3. */
data class SweepState(
    val roundId: String,
    val deckId: String,
    val startedAt: Long,
    val currentEquipmentId: String? = null,
    val graded: Map<String, ConditionGrade> = emptyMap(),
) {
    val doneCount: Int get() = graded.size
    val deficiencyCount: Int
        get() = graded.values.count {
            it == ConditionGrade.DEFECTIVE || it == ConditionGrade.OUT_OF_SERVICE
        }
}

/** Everything the Vessel tab renders from. */
data class DeckViewUiState(
    val isLoading: Boolean = true,
    val vessel: Vessel? = null,
    val model: StackRenderModel = StackRenderModel(),
    val mode: DeckViewMode = DeckViewMode.STACK,
    val focusedDeckId: String? = null,
    val isoAngleDeg: Float = IsoProjection.DEFAULT_ANGLE_DEG,
    val gridSnapEnabled: Boolean = false,
    val showGrid: Boolean = false,
    val flatInDeckMode: Boolean = false,
    val sweep: SweepState? = null,
    val presets: List<PlanPreset> = BuiltInPlanPresets.all,
) {
    val hasVessel: Boolean get() = vessel != null
    val hasNoDecks: Boolean get() = !isLoading && hasVessel && model.decks.isEmpty()

    /** The deck the FAB and the sweep toggle act on: the focused one, or the only one there is. */
    val activeDeck: DeckNode?
        get() = model.deck(focusedDeckId) ?: model.decks.singleOrNull()

    /** In deck mode the projection collapses to the flat plan when the officer asks for it (§7.1B). */
    val effectiveAngleDeg: Float
        get() = if (mode == DeckViewMode.DECK && flatInDeckMode) 0f else isoAngleDeg
}

/**
 * The Vessel tab's view model — §7.
 *
 * Combines the active vessel, its decks, their zones, the vessel's equipment and the catalogue into
 * one [StackRenderModel] the canvas can draw without touching a repository, and owns everything that
 * outlives a gesture: the per-vessel view mode (§7.1), the focused deck, and the sweep round of
 * §7.3. Gesture-frame state — pan, zoom, spread — deliberately lives in the canvas's own
 * [com.deckwatch.feature.deckview.render.DeckTransformState] so that dragging never recomposes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeckViewViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val referenceRepository: ReferenceRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    /** Epoch-day treated as today when classifying overdue equipment (§11.1). */
    @VisibleForTesting
    internal var today: () -> Long = Dates::todayEpochDay

    /** Epoch-millis clock for round timestamps. */
    @VisibleForTesting
    internal var clock: () -> Long = Dates::nowMillis

    /** New record ids — UUIDv4 per §6. */
    @VisibleForTesting
    internal var newId: () -> String = { UUID.randomUUID().toString() }

    private val viewState = MutableStateFlow(ViewState())

    val uiState: StateFlow<DeckViewUiState> = combine(
        renderModel(),
        preferences.userPreferences,
        referenceRepository.observePlanPresets().map(BuiltInPlanPresets::merge),
        viewState,
    ) { render, prefs, presets, view ->
        val vesselId = render.vessel?.id
        val decks = render.model.decks
        val focused = view.focusedDeckId.takeIf { id -> decks.any { it.deckId == id } }
        DeckViewUiState(
            isLoading = render.isLoading,
            vessel = render.vessel,
            model = render.model,
            mode = view.modeByVessel[vesselId] ?: DeckViewMode.STACK,
            focusedDeckId = focused,
            isoAngleDeg = IsoProjection.clampAngle(prefs.isoAngleDeg),
            gridSnapEnabled = prefs.gridSnapEnabled,
            showGrid = view.showGrid,
            flatInDeckMode = view.flatInDeckMode,
            sweep = view.sweep?.takeIf { sweep -> decks.any { it.deckId == sweep.deckId } },
            presets = presets,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DeckViewUiState(),
    )

    // ------------------------------------------------------------------ view state

    fun setMode(mode: DeckViewMode) {
        val current = uiState.value
        val vesselId = current.vessel?.id
        // Deck mode has to have a deck: coming from an unfocused stack, take the topmost one.
        val focus = if (mode == DeckViewMode.DECK && current.focusedDeckId == null) {
            current.model.decksTopFirst.firstOrNull()?.deckId
        } else {
            current.focusedDeckId
        }
        viewState.update {
            it.copy(
                modeByVessel = it.modeByVessel + (vesselId to mode),
                focusedDeckId = focus ?: it.focusedDeckId,
            )
        }
    }

    /** Tap on a deck surface: focus it in stack mode, enter deck mode on a second tap (§7.2). */
    fun focusDeck(deckId: String?) {
        viewState.update { it.copy(focusedDeckId = deckId) }
    }

    fun enterDeckMode(deckId: String) {
        val vesselId = uiState.value.vessel?.id
        viewState.update {
            it.copy(
                focusedDeckId = deckId,
                modeByVessel = it.modeByVessel + (vesselId to DeckViewMode.DECK),
            )
        }
    }

    /** The flat / isometric toggle of §7.1B — one animated float, not a second renderer. */
    fun toggleFlat() {
        viewState.update { it.copy(flatInDeckMode = !it.flatInDeckMode) }
    }

    fun toggleGrid() {
        viewState.update { it.copy(showGrid = !it.showGrid) }
    }

    /** Persists the officer's isometric angle (§18 setting, §7.2 renderer). */
    fun setIsoAngle(angleDeg: Float) {
        viewModelScope.launch { preferences.setIsoAngleDeg(IsoProjection.clampAngle(angleDeg)) }
    }

    fun setGridSnap(enabled: Boolean) {
        viewModelScope.launch { preferences.setGridSnapEnabled(enabled) }
    }

    // ------------------------------------------------------------------ equipment placement

    /**
     * Commits a marker drag — §7.2. The zone is re-inferred from the drop point, so dragging an item
     * into "Pump Room" files it there without a second dialog.
     */
    fun moveEquipment(equipmentId: String, deckId: String, posX: Float, posY: Float) {
        val zoneId = zoneAt(deckId, posX, posY)
        viewModelScope.launch {
            equipmentRepository.move(
                id = equipmentId,
                deckId = deckId,
                zoneId = zoneId,
                posX = posX.coerceIn(0f, 1f),
                posY = posY.coerceIn(0f, 1f),
            )
        }
    }

    /** The zone containing a plan point, for the "add equipment here" flow of §7.5 step 3. */
    fun zoneAt(deckId: String, posX: Float, posY: Float): String? =
        uiState.value.model.deck(deckId)?.zones
            ?.firstOrNull { zone -> Polygons.contains(zone.polygon, Vec2(posX, posY)) }
            ?.zoneId

    /** First-run empty state: one tap on a preset builds the deck (§14, §6.3). */
    fun createDeckFromPreset(preset: PlanPreset, name: String) {
        viewModelScope.launch {
            val vesselId = vesselRepository.observeActiveVessel().first()?.id ?: return@launch
            vesselRepository.addDeckAbove(
                vesselId = vesselId,
                name = name,
                shortCode = preset.suggestedShortCode,
                plan = preset.plan,
            )
        }
    }

    // ------------------------------------------------------------------ sweep mode — §7.3

    /**
     * Starts a sweep of [deckId], writing the `RoundEntity` immediately so a sweep interrupted by a
     * phone call is still a record (§7.3, C10).
     *
     * @param title supplied by the caller so the round's title comes from `strings.xml` (C8).
     */
    fun startSweep(deckId: String, title: String) {
        val state = uiState.value
        val deck = state.model.deck(deckId) ?: return
        val vesselId = state.vessel?.id ?: return
        val round = Round(
            id = newId(),
            vesselId = vesselId,
            templateKey = sweepTemplateKey(deck.shortCode),
            title = title,
            startedAt = clock(),
            itemCount = deck.markers.size,
        )
        viewModelScope.launch { inspectionRepository.upsertRound(round) }
        viewState.update {
            it.copy(
                sweep = SweepState(
                    roundId = round.id,
                    deckId = deckId,
                    startedAt = round.startedAt,
                    currentEquipmentId = SweepOrder.first(deck.markers),
                ),
                focusedDeckId = deckId,
            )
        }
    }

    /**
     * Records a grade during a sweep and advances — §7.3.
     *
     * The condition itself is written by the equipment sheet; this adds the `RoundItem` and moves the
     * sheet on to the next ungraded item on the same deck. When nothing is left the round finishes
     * itself.
     */
    fun onSweepGraded(equipmentId: String, grade: ConditionGrade) {
        val state = uiState.value
        val sweep = state.sweep ?: return
        val deck = state.model.deck(sweep.deckId) ?: return
        val at = clock()
        viewModelScope.launch {
            inspectionRepository.upsertRoundItem(
                RoundItem(
                    id = roundItemId(sweep.roundId, equipmentId),
                    roundId = sweep.roundId,
                    equipmentId = equipmentId,
                    checkedAt = at,
                    condition = grade,
                ),
            )
        }
        val graded = sweep.graded + (equipmentId to grade)
        val next = SweepOrder.next(deck.markers, graded.keys, equipmentId)
        val advanced = sweep.copy(graded = graded, currentEquipmentId = next)
        if (next == null) {
            finishSweep(advanced)
        } else {
            viewState.update { it.copy(sweep = advanced) }
        }
    }

    /** Ends the sweep — the top-bar toggle, or the last item on the deck (§7.3). */
    fun finishSweep() {
        finishSweep(uiState.value.sweep ?: return)
    }

    private fun finishSweep(sweep: SweepState) {
        val deck = uiState.value.model.deck(sweep.deckId)
        val completedAt = clock()
        viewModelScope.launch {
            val existing = inspectionRepository.getRound(sweep.roundId) ?: return@launch
            inspectionRepository.upsertRound(
                existing.copy(
                    completedAt = completedAt,
                    itemCount = deck?.markers?.size ?: existing.itemCount,
                    doneCount = sweep.doneCount,
                    deficiencyCount = sweep.deficiencyCount,
                ),
            )
        }
        viewState.update { it.copy(sweep = null) }
    }

    // ------------------------------------------------------------------ plumbing

    private data class ViewState(
        val modeByVessel: Map<String?, DeckViewMode> = emptyMap(),
        val focusedDeckId: String? = null,
        val showGrid: Boolean = false,
        val flatInDeckMode: Boolean = false,
        val sweep: SweepState? = null,
    )

    private data class RenderSlice(
        val vessel: Vessel?,
        val model: StackRenderModel,
        val isLoading: Boolean,
    )

    private fun renderModel(): Flow<RenderSlice> =
        vesselRepository.observeActiveVessel().flatMapLatest { vessel ->
            if (vessel == null) {
                flowOf(RenderSlice(null, StackRenderModel(), isLoading = false))
            } else {
                vesselRepository.observeDecks(vessel.id).flatMapLatest { decks ->
                    combine(
                        zonesByDeck(decks.map { it.id }),
                        equipmentRepository.observeEquipment(vessel.id),
                        referenceRepository.observeEquipmentTypes(),
                        preferences.userPreferences.map { it.language },
                    ) { zones, equipment, types, language ->
                        val names = types.associate { type ->
                            type.typeKey to
                                if (language == AppLanguage.TURKISH) type.nameTr else type.nameEn
                        }
                        RenderSlice(
                            vessel = vessel,
                            model = RenderModelAssembler.assemble(
                                vesselId = vessel.id,
                                vesselName = vessel.name,
                                decks = decks,
                                zonesByDeck = zones,
                                equipment = equipment,
                                typeNames = names,
                                today = today(),
                            ),
                            isLoading = false,
                        )
                    }
                }
            }
        }

    /** `combine` of an empty list never emits, so a vessel with no decks short-circuits. */
    private fun zonesByDeck(deckIds: List<String>): Flow<Map<String, List<Zone>>> =
        if (deckIds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                deckIds.map { id -> vesselRepository.observeZones(id).map { id to it } },
            ) { pairs -> pairs.toMap() }
        }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** §7.3: a sweep round is keyed by the deck it walked. */
        fun sweepTemplateKey(deckShortCode: String): String = "SWEEP_$deckShortCode"

        /**
         * Round items are keyed deterministically by round + equipment, so grading the same item
         * twice in one sweep updates the row instead of duplicating it (§6.7).
         */
        fun roundItemId(roundId: String, equipmentId: String): String = "$roundId:$equipmentId"
    }
}
