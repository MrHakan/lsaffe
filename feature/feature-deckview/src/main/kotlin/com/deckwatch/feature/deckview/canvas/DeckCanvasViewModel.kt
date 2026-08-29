package com.deckwatch.feature.deckview.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the deck canvas draws — §7.1 A, §7.2. */
data class DeckCanvasUiState(
    val loading: Boolean = true,
    val vessel: Vessel? = null,
    /** Every deck of the vessel, highest first, which is how a general arrangement is read. */
    val decks: List<Deck> = emptyList(),
    val selectedDeck: Deck? = null,
    val zones: List<Zone> = emptyList(),
    /** Only the items placed on the selected deck; unplaced items have no position to draw. */
    val equipment: List<Equipment> = emptyList(),
    val types: Map<String, EquipmentType> = emptyMap(),
    /** Isometric angle from settings — 0° is the flat plan (§18). */
    val isoAngleDeg: Float = 30f,
    val gridSnapEnabled: Boolean = false,
) {
    val hasDecks: Boolean get() = decks.isNotEmpty()
}

/**
 * State and writes for the 2.5D deck canvas — §7.1 A, §7.2, §7.5.
 *
 * The canvas is a *placement* surface: it moves items and starts new ones at a point. It never
 * creates or deletes records itself — those journeys already belong to the add sheet and the
 * equipment sheet, and duplicating them here would give the app two ways to do the same thing that
 * could drift apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeckCanvasViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    referenceRepository: ReferenceRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val selectedDeckId = MutableStateFlow<String?>(null)

    /** The active vessel and its decks, highest first — a general arrangement reads downwards. */
    private val vesselWithDecks: Flow<Pair<Vessel?, List<Deck>>> =
        vesselRepository.observeActiveVessel().flatMapLatest { vessel ->
            if (vessel == null) {
                flowOf(null to emptyList())
            } else {
                vesselRepository.observeDecks(vessel.id)
                    .map { decks -> vessel to decks.sortedByDescending { it.levelIndex } }
            }
        }

    /**
     * Which deck is being drawn. Resolved here rather than inside the content flow so that picking
     * another deck re-subscribes its zones — zones belong to a deck, and a selection that did not
     * re-subscribe would draw the new deck under the old deck's zones.
     */
    private val selection: Flow<Selection> =
        combine(vesselWithDecks, selectedDeckId) { (vessel, decks), pinned ->
            Selection(
                vessel = vessel,
                decks = decks,
                deck = decks.firstOrNull { it.id == pinned } ?: decks.firstOrNull(),
            )
        }.distinctUntilChanged()

    val uiState: StateFlow<DeckCanvasUiState> = selection
        .flatMapLatest { (vessel, decks, deck) ->
            if (vessel == null) {
                flowOf(DeckCanvasUiState(loading = false))
            } else {
                combine(
                    if (deck == null) flowOf(emptyList()) else vesselRepository.observeZones(deck.id),
                    equipmentRepository.observeEquipment(vessel.id),
                    referenceRepository.observeEquipmentTypes(),
                    preferences.userPreferences,
                ) { zones, equipment, types, prefs ->
                    DeckCanvasUiState(
                        loading = false,
                        vessel = vessel,
                        decks = decks,
                        selectedDeck = deck,
                        zones = zones,
                        equipment = equipment.filter { it.deckId != null && it.deckId == deck?.id },
                        types = types.associateBy { it.typeKey },
                        isoAngleDeg = prefs.isoAngleDeg,
                        gridSnapEnabled = prefs.gridSnapEnabled,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DeckCanvasUiState(),
        )

    /** The vessel, its decks and the one being drawn — the input to the content flow. */
    private data class Selection(val vessel: Vessel?, val decks: List<Deck>, val deck: Deck?)

    /** Show another deck. Passing an id that is not on this vessel falls back to the top deck. */
    fun selectDeck(deckId: String) {
        selectedDeckId.value = deckId
    }

    /**
     * Drops an item at a point on the current deck — §7.3.
     *
     * The zone is derived from the point rather than asked for: the officer has already said where
     * the thing is by putting their finger on it, and a zone is a region of the deck, so which one
     * it lands in is arithmetic, not a decision.
     */
    fun moveTo(equipmentId: String, point: PlanPoint) {
        val state = uiState.value
        val deck = state.selectedDeck ?: return
        val snapped = snap(point, state.gridSnapEnabled)
        val zoneId = state.zones.firstOrNull { PlanGeometry.contains(it.polygon, snapped) }?.id
        viewModelScope.launch {
            equipmentRepository.move(equipmentId, deck.id, zoneId, snapped.x, snapped.y)
        }
    }

    /** The zone a point falls in on the current deck, for handing to the add sheet. */
    fun zoneAt(point: PlanPoint): String? =
        uiState.value.zones.firstOrNull { PlanGeometry.contains(it.polygon, point) }?.id

    /** Applies the §18 grid snap, if it is on. */
    fun snap(point: PlanPoint, enabled: Boolean = uiState.value.gridSnapEnabled): PlanPoint {
        if (!enabled) return PlanPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        return PlanPoint(
            x = snapAxis(point.x),
            y = snapAxis(point.y),
        )
    }

    private fun snapAxis(value: Float): Float =
        (Math.round(value.coerceIn(0f, 1f) * GRID_DIVISIONS).toFloat() / GRID_DIVISIONS)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** A 20×20 grid: fine enough to line a row of extinguishers up, coarse enough to feel. */
        const val GRID_DIVISIONS = 20f
    }
}
