package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.TaskDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One line of the matrix: a task definition seen through the equipment type it applies to. */
data class IntervalRow(
    val taskKey: String,
    val title: String,
    val intervalKind: IntervalKind,
    val intervalMonths: Int?,
    val performedBy: PerformedBy,
    /** Flag codes with a recorded divergence — drives the star marker (§8.3). */
    val divergentFlags: List<String> = emptyList(),
    /** The card this row opens; null when no `regulationRef` resolves to a bundled card. */
    val cardRefKey: String? = null,
    val cardCitation: String? = null,
) {
    val hasFlagDivergence: Boolean get() = divergentFlags.isNotEmpty()
}

/** Rows grouped under their equipment type. [typeKey] is null for the trailing catch-all group. */
data class IntervalMatrixGroup(
    val typeKey: String?,
    val typeName: String,
    val rows: List<IntervalRow>,
)

data class IntervalMatrixUiState(
    val groups: List<IntervalMatrixGroup> = emptyList(),
    val query: String = "",
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val rowCount: Int get() = groups.sumOf { it.rows.size }
}

@HiltViewModel
class IntervalMatrixViewModel @Inject constructor(
    reference: ReferenceRepository,
    maintenance: MaintenanceRepository,
) : ViewModel() {

    private val queryState = MutableStateFlow("")

    val uiState: StateFlow<IntervalMatrixUiState> = combine(
        reference.observeEquipmentTypes(),
        maintenance.observeTaskDefinitions(),
        reference.observeRegulationCards(),
        queryState,
    ) { types, definitions, cards, query ->
        IntervalMatrixUiState(
            groups = buildIntervalMatrix(types, definitions, cards, query),
            query = query,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionTimeoutMillis),
        initialValue = IntervalMatrixUiState(),
    )

    fun onQueryChange(value: String) {
        queryState.value = value
    }

    private companion object {
        const val SubscriptionTimeoutMillis = 5_000L
    }
}

/**
 * Assemble the equipment-type × interval × performed-by matrix — §8.3.
 *
 * A definition belongs to a type when either side says so: the definition lists the type in
 * `appliesToTypeKeys`, or the catalogue entry lists the definition in `taskKeys`. The two
 * directions are seeded independently (§9.1, §6.6) and neither is guaranteed complete, so taking
 * the union is what keeps the matrix honest.
 *
 * Definitions that match no catalogue type are not discarded: they appear in a trailing group with
 * a null [IntervalMatrixGroup.typeKey], because a task the officer cannot see is worse than an
 * untidy table.
 *
 * [query] filters case-insensitively on the type name, the task title and the task key. A group
 * whose type name matches keeps all of its rows.
 */
internal fun buildIntervalMatrix(
    types: List<EquipmentType>,
    definitions: List<TaskDefinition>,
    cards: List<RegulationCard>,
    query: String = "",
): List<IntervalMatrixGroup> {
    val citations = cards.associate { it.refKey to it.citation }
    val needle = query.trim()
    val claimed = mutableSetOf<String>()

    val typeGroups = types
        .sortedBy { it.nameEn }
        .mapNotNull { type ->
            val rows = definitions
                .filter { definition -> definition.appliesTo(type) }
                .onEach { claimed += it.key }
                .sortedBy { it.titleEn }
                .map { it.toRow(citations) }
            val kept = filterRows(rows, needle, typeMatches = type.nameEn.containsIgnoreCase(needle))
            kept.takeIf { it.isNotEmpty() }
                ?.let { IntervalMatrixGroup(type.typeKey, type.nameEn, it) }
        }

    val orphanRows = definitions
        .filterNot { it.key in claimed }
        .sortedBy { it.titleEn }
        .map { it.toRow(citations) }
        .let { filterRows(it, needle, typeMatches = false) }

    return if (orphanRows.isEmpty()) {
        typeGroups
    } else {
        typeGroups + IntervalMatrixGroup(typeKey = null, typeName = "", rows = orphanRows)
    }
}

private fun TaskDefinition.appliesTo(type: EquipmentType): Boolean =
    type.typeKey in appliesToTypeKeys || key in type.taskKeys

private fun TaskDefinition.toRow(citations: Map<String, String>): IntervalRow {
    val refKey = regulationRefs.firstOrNull { it in citations } ?: regulationRefs.firstOrNull()
    return IntervalRow(
        taskKey = key,
        title = titleEn,
        intervalKind = intervalKind,
        intervalMonths = intervalMonths,
        performedBy = performedBy,
        divergentFlags = flagOverrides.orEmpty().keys.sorted(),
        cardRefKey = refKey?.takeIf { it in citations },
        cardCitation = refKey?.let(citations::get),
    )
}

private fun filterRows(rows: List<IntervalRow>, needle: String, typeMatches: Boolean): List<IntervalRow> = when {
    needle.isEmpty() || typeMatches -> rows
    else -> rows.filter { it.title.containsIgnoreCase(needle) || it.taskKey.containsIgnoreCase(needle) }
}

private fun String.containsIgnoreCase(needle: String): Boolean =
    needle.isEmpty() || contains(needle, ignoreCase = true)
