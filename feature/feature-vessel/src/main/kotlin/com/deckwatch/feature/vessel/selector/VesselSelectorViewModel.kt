package com.deckwatch.feature.vessel.selector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VesselSelectorUiState(
    val vessels: List<Vessel> = emptyList(),
    val active: Vessel? = null,
)

/** Backs the persistent vessel selector that lives in the app bar of tabs 2–4 (§5). */
@HiltViewModel
class VesselSelectorViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
) : ViewModel() {

    val uiState: StateFlow<VesselSelectorUiState> = combine(
        vesselRepository.observeVessels(),
        vesselRepository.observeActiveVessel(),
    ) { vessels, active -> VesselSelectorUiState(vessels = vessels, active = active) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = VesselSelectorUiState(),
        )

    fun select(vesselId: String) {
        viewModelScope.launch { vesselRepository.setActiveVessel(vesselId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
