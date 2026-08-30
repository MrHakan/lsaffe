package com.deckwatch.feature.notes.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.TaskDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One group of the catalogue, with its types, for the guide's index — §9.1. */
data class EquipmentGroupSummary(
    val group: EquipmentGroup,
    val typeCount: Int,
)

/** One row of a group's type list. */
data class EquipmentTypeRow(
    val typeKey: String,
    val name: String,
    val subGroup: String,
    val symbolKey: String,
    /** How much guide content the type carries: shown so an officer can see where the depth is. */
    val noteCount: Int,
    val taskCount: Int,
    val ruleCount: Int,
)

/** The index: every group, or one group's types — §9.1. */
data class EquipmentGuideUiState(
    val loading: Boolean = true,
    val groups: List<EquipmentGroupSummary> = emptyList(),
    val group: EquipmentGroup? = null,
    /** Types of [group], or of every group when none is selected, in catalogue order. */
    val types: List<EquipmentTypeRow> = emptyList(),
    val query: String = "",
)

/**
 * The equipment guide index — §9.1.
 *
 * Search runs across the type name, its sub-group and the guide text itself, because "SCBA",
 * "cylinder" and "Storz" are all things an officer would type looking for a page whose *title*
 * contains none of them.
 */
@HiltViewModel
class EquipmentGuideViewModel @Inject constructor(
    private val referenceRepository: ReferenceRepository,
) : ViewModel() {

    private val group = MutableStateFlow<EquipmentGroup?>(null)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<EquipmentGuideUiState> = combine(
        referenceRepository.observeEquipmentTypes(),
        group,
        query,
    ) { types, selectedGroup, search ->
        val visible = types
            .filter { selectedGroup == null || it.group == selectedGroup }
            .filter { it.matches(search) }
            .sortedWith(compareBy({ it.subGroup }, { it.nameEn }))
        EquipmentGuideUiState(
            loading = false,
            groups = EquipmentGroup.entries
                .map { entry -> EquipmentGroupSummary(entry, types.count { it.group == entry }) }
                .filter { it.typeCount > 0 },
            group = selectedGroup,
            types = visible.map { it.toRow() },
            query = search,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EquipmentGuideUiState())

    fun setGroup(value: EquipmentGroup?) {
        group.value = value
        query.value = ""
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /**
     * Matches the name, the sub-group, the type key and the guide text itself.
     *
     * Searching the bullets, not only the headings, is the point: an officer types "Storz" or
     * "weak link" — words that appear nowhere in any type's *name* — and expects to land on the
     * page that explains them.
     */
    private fun EquipmentType.matches(search: String): Boolean {
        if (search.isBlank()) return true
        val needle = search.trim().lowercase()
        return nameEn.lowercase().contains(needle) ||
            subGroup.lowercase().contains(needle) ||
            typeKey.lowercase().contains(needle) ||
            technicalNotes.any { note ->
                note.heading.lowercase().contains(needle) ||
                    note.bullets.any { it.lowercase().contains(needle) }
            }
    }

    private fun EquipmentType.toRow() = EquipmentTypeRow(
        typeKey = typeKey,
        name = nameEn,
        subGroup = subGroup,
        symbolKey = symbolKey,
        noteCount = technicalNotes.size,
        taskCount = taskKeys.size,
        ruleCount = regulationRefs.size,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Everything one equipment type's page shows — §9.1, §8.2, §11.1. */
data class EquipmentTypeDetailUiState(
    val loading: Boolean = true,
    val type: EquipmentType? = null,
    /** The type's scheduled tasks, resolved from the task definitions. */
    val tasks: List<TaskDefinition> = emptyList(),
    /** The regulation cards the type points at, in the order the catalogue lists them. */
    val cards: List<RegulationCard> = emptyList(),
)

/**
 * One equipment type's page — §9.1.
 *
 * Read-only: this is the reference side of the app, so nothing here writes. The tasks and cards are
 * resolved from their own repositories rather than copied into the catalogue, so a corrected
 * interval or a rewritten card shows up here the moment the content is re-imported.
 */
@HiltViewModel
class EquipmentTypeDetailViewModel @Inject constructor(
    private val referenceRepository: ReferenceRepository,
    private val maintenanceRepository: MaintenanceRepository,
) : ViewModel() {

    private val typeKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EquipmentTypeDetailUiState> = combine(
        referenceRepository.observeEquipmentTypes(),
        referenceRepository.observeRegulationCards(),
        maintenanceRepository.observeTaskDefinitions(),
        typeKey,
    ) { types, cards, definitions, key ->
        val type = types.firstOrNull { it.typeKey == key }
        if (type == null) {
            EquipmentTypeDetailUiState(loading = key == null)
        } else {
            val byKey = definitions.associateBy { it.key }
            val byRef = cards.associateBy { it.refKey }
            EquipmentTypeDetailUiState(
                loading = false,
                type = type,
                // mapNotNull, not a filter: a task key with no definition is a content gap, and
                // showing an empty row for it would be worse than leaving it out.
                tasks = type.taskKeys.mapNotNull(byKey::get),
                cards = type.regulationRefs.mapNotNull(byRef::get),
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        EquipmentTypeDetailUiState(),
    )

    fun bind(key: String) {
        typeKey.value = key
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
