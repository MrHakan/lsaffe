package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Top level of the Notes tab: the six section tiles with their counts, plus global search — §8.1. */
data class NotesHomeUiState(
    val sectionCounts: Map<RegulationSection, Int> = emptyMap(),
    val query: String = "",
    val results: List<RegulationCard> = emptyList(),
) {
    /** True once the officer has typed something: the tiles give way to the result list. */
    val isSearching: Boolean get() = query.isNotBlank()

    fun countFor(section: RegulationSection): Int = sectionCounts[section] ?: 0
}

@HiltViewModel
class NotesHomeViewModel @Inject constructor(
    private val reference: ReferenceRepository,
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()

    /**
     * Card counts per section. MY NOTES counts the user's own notes rather than bundled cards —
     * it is the one section whose content the officer writes.
     */
    private val sectionCounts: Flow<Map<RegulationSection, Int>> = combine(
        reference.observeRegulationCards(),
        reference.observeUserNotes(),
    ) { cards, notes ->
        val bundled = cards.groupingBy { it.section }.eachCount()
        RegulationSection.entries.associateWith { section ->
            when (section) {
                RegulationSection.MY_NOTES -> notes.size
                else -> bundled[section] ?: 0
            }
        }
    }

    /**
     * Search runs through the repository (§8.1) so the Room implementation can push the match down
     * into SQL; a blank query short-circuits without touching it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val results: Flow<List<RegulationCard>> = queryState.flatMapLatest { raw ->
        if (raw.isBlank()) flowOf(emptyList()) else reference.searchRegulationCards(raw.trim())
    }

    val uiState: StateFlow<NotesHomeUiState> = combine(
        sectionCounts,
        queryState,
        results,
    ) { counts, currentQuery, matches ->
        NotesHomeUiState(
            sectionCounts = counts,
            query = currentQuery,
            // `results` lags the query by one emission while the search flow restarts; pinning it
            // to blank keeps the state internally consistent instead of briefly showing stale hits.
            results = if (currentQuery.isBlank()) emptyList() else matches,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionTimeoutMillis),
        initialValue = NotesHomeUiState(),
    )

    fun onQueryChange(value: String) {
        queryState.value = value
    }

    fun clearQuery() {
        queryState.value = ""
    }

    private companion object {
        const val SubscriptionTimeoutMillis = 5_000L
    }
}
