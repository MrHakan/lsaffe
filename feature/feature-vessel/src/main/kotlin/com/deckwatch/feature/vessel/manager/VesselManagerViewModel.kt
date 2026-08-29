package com.deckwatch.feature.vessel.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.common.ImoStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One vessel as the manager list needs it: the record plus its IMO validation state. */
data class VesselRow(
    val vessel: Vessel,
    val imoStatus: ImoStatus,
) {
    val id: String get() = vessel.id
    val isActive: Boolean get() = vessel.isActive
}

data class VesselManagerUiState(
    val vessels: List<VesselRow> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && vessels.isEmpty()
}

/**
 * The vessel list of §5: many vessels, exactly one active. Deletion cascades in the repository
 * (§6.2 `onDelete = CASCADE`); this view model only asks for it after the user has confirmed.
 */
@HiltViewModel
class VesselManagerViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<String?>(null)

    /** The vessel the confirm dialog is currently about, if any. */
    val deleteTarget: StateFlow<String?> = pendingDelete

    val uiState: StateFlow<VesselManagerUiState> = vesselRepository.observeVessels()
        .map { vessels ->
            VesselManagerUiState(
                vessels = vessels.map { VesselRow(it, ImoStatus.of(it.imoNumber)) },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = VesselManagerUiState(),
        )

    fun setActive(vesselId: String) {
        viewModelScope.launch { vesselRepository.setActiveVessel(vesselId) }
    }

    fun askDelete(vesselId: String) {
        pendingDelete.value = vesselId
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete(vesselId: String) {
        pendingDelete.value = null
        viewModelScope.launch { vesselRepository.deleteVessel(vesselId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
