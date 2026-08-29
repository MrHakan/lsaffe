package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The tab chrome's own state: whether the officer has dismissed the repeating disclaimer strip.
 *
 * It starts hidden and appears once the stored preference says it should, so a device that has it
 * dismissed never flashes the strip on the way in.
 */
@HiltViewModel
class NotesChromeViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val footerVisible: StateFlow<Boolean> = preferences.userPreferences
        .map { !it.notesFooterDismissed }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

    fun dismissFooter() {
        viewModelScope.launch { preferences.setNotesFooterDismissed(true) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
