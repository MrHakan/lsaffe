package com.deckwatch.feature.deckview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The tab frame's own slice of state: which vessel is active.
 *
 * The list body keeps its own view model — this one exists because the actions *around* the list
 * (add equipment, open the deck manager) need the active vessel's id before the list has anything
 * to show.
 */
@HiltViewModel
class VesselTabViewModel @Inject constructor(
    vesselRepository: VesselRepository,
) : ViewModel() {

    val activeVessel: StateFlow<Vessel?> = vesselRepository.observeActiveVessel()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
