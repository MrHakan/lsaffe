package com.deckwatch.feature.vessel.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ZoneManagerUiState(
    val deck: Deck? = null,
    val zones: List<Zone> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && zones.isEmpty()
}

/** Fields the zone dialog collects. The polygon is derived from [rect] on save. */
data class ZoneDraft(
    val id: String? = null,
    val name: String,
    val colorArgb: Int,
    val rect: ZoneRect,
)

/**
 * Spatial zones on one deck (§6.4). Ordering is explicit via `sortOrder` and moved with up/down
 * buttons — a drag-to-reorder list is unusable with gloves on a moving deck (C6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ZoneManagerViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
) : ViewModel() {

    private val deckId = MutableStateFlow<String?>(null)
    private val deckFlow = MutableStateFlow<Deck?>(null)
    private val pendingDelete = MutableStateFlow<String?>(null)

    val deleteTarget: StateFlow<String?> = pendingDelete

    val uiState: StateFlow<ZoneManagerUiState> = combine(
        deckId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else vesselRepository.observeZones(id)
        },
        deckFlow,
    ) { zones, deck -> ZoneManagerUiState(deck = deck, zones = zones, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ZoneManagerUiState(),
        )

    fun bind(deckId: String) {
        if (this.deckId.value == deckId) return
        this.deckId.value = deckId
        viewModelScope.launch { deckFlow.value = vesselRepository.getDeck(deckId) }
    }

    /**
     * Creates or updates a zone. New zones land at the end of the current order.
     *
     * The existing record is read from the repository rather than from [uiState], so a save works
     * even when nothing is collecting the state yet.
     */
    fun save(draft: ZoneDraft) {
        val deck = deckId.value ?: return
        viewModelScope.launch {
            val zones = currentZones()
            val existing = draft.id?.let { id -> zones.firstOrNull { it.id == id } }
            val sortOrder = existing?.sortOrder ?: ((zones.maxOfOrNull { it.sortOrder } ?: -1) + 1)
            vesselRepository.upsertZone(
                Zone(
                    id = draft.id ?: UUID.randomUUID().toString(),
                    deckId = deck,
                    name = draft.name.trim(),
                    polygon = ZoneGeometry.rectToPolygon(draft.rect),
                    colorArgb = draft.colorArgb,
                    sortOrder = sortOrder,
                ),
            )
        }
    }

    fun moveUp(zoneId: String) = swapWithNeighbour(zoneId, -1)

    fun moveDown(zoneId: String) = swapWithNeighbour(zoneId, +1)

    fun askDelete(zoneId: String) {
        pendingDelete.value = zoneId
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete(zoneId: String) {
        pendingDelete.value = null
        viewModelScope.launch { vesselRepository.deleteZone(zoneId) }
    }

    /**
     * Swaps a zone with its neighbour by exchanging `sortOrder`, which keeps the values dense and
     * needs no renumbering pass.
     */
    private fun swapWithNeighbour(zoneId: String, offset: Int) {
        viewModelScope.launch {
            val ordered = currentZones()
            val index = ordered.indexOfFirst { it.id == zoneId }
            val otherIndex = index + offset
            if (index < 0 || otherIndex !in ordered.indices) return@launch
            val zone = ordered[index]
            val other = ordered[otherIndex]
            vesselRepository.upsertZone(zone.copy(sortOrder = other.sortOrder))
            vesselRepository.upsertZone(other.copy(sortOrder = zone.sortOrder))
        }
    }

    private suspend fun currentZones(): List<Zone> {
        val id = deckId.value ?: return emptyList()
        return vesselRepository.observeZones(id).first()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
