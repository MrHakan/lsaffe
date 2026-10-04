package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.UserNote
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Unified offline search across regulations, bilingual equipment guides and personal notes. */
data class NotesHomeUiState(
    val sectionCounts: Map<RegulationSection, Int> = emptyMap(),
    val query: String = "",
    val results: List<RegulationCard> = emptyList(),
    /** How many types the equipment guide covers — the count on its tile. */
    val equipmentTypeCount: Int = 0,
    val equipmentResults: List<EquipmentType> = emptyList(),
    val noteResults: List<UserNote> = emptyList(),
    val publications: List<RegulationCard> = emptyList(),
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

    val uiState: StateFlow<NotesHomeUiState> = combine(
        queryState,
        reference.observeRegulationCards(),
        reference.observeEquipmentTypes(),
        reference.observeUserNotes(),
    ) { currentQuery, cards, types, notes ->
        val needle = currentQuery.trim()
        val searching = needle.isNotEmpty()
        fun matches(vararg text: String) = searching && text.any { it.contains(needle, ignoreCase = true) }
        NotesHomeUiState(
            sectionCounts = RegulationSection.entries.associateWith { section ->
                if (section == RegulationSection.MY_NOTES) notes.size else cards.count { it.section == section }
            },
            equipmentTypeCount = types.size,
            query = currentQuery,
            results = cards.filter {
                matches(it.citation, it.title, it.what, it.summaryTr, it.sourceRef, it.detailBullets.joinToString(" "))
            },
            equipmentResults = types.filter {
                matches(it.nameEn, it.nameTr, it.typeKey, it.helpTextEn, it.helpTextTr,
                    it.technicalNotes.joinToString(" ") { note -> note.heading + " " + note.bullets.joinToString(" ") })
            },
            noteResults = notes.filter { matches(it.title, it.body, it.folder) }.sortedByDescending { it.updatedAt },
            publications = cards.filter { it.refKey.startsWith("IMO_PUBLICATION_") },
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
