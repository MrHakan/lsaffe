package com.deckwatch.feature.inspection

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import kotlinx.serialization.Serializable

/**
 * A bilingual label carried through the UI layer — C8. Regulatory *content* stays English, but a
 * task title has an authored Turkish form in [com.deckwatch.core.model.TaskDefinition.titleTr], so
 * both travel together and the composable resolves against the device locale.
 */
@Serializable
data class LocalisedText(val en: String, val tr: String = "") {
    /** Turkish when asked for and actually authored; English otherwise. */
    fun resolve(turkish: Boolean): String = if (turkish && tr.isNotBlank()) tr else en

    companion object {
        val Empty = LocalisedText("", "")
    }
}

/**
 * The five segments of the Due tab — §12.
 *
 * They **partition** the open work list: every open task instance lands in exactly one segment, so
 * the chip counts add up to the total and no row is shown twice. See [DueBucketing.segmentOf] for
 * the boundary rules.
 */
@Serializable
enum class DueSegment {
    /** Past the tolerance window — §11.4. */
    OVERDUE,

    /** Due within the next seven days (or inside tolerance and already past the nominal date). */
    THIS_WEEK,

    /** Due in 8–30 days. */
    THIS_MONTH,

    /** Beyond 30 days but on or before the Safety Equipment Certificate expiry — §12 survey prep. */
    BEFORE_SURVEY,

    /** Everything further out, plus anything the officer has deferred. */
    PLANNED,
}

/**
 * The Due tab's filter set — §12. Every dimension is independent and they combine with AND, so
 * "LSA + shore provider + Upper Deck" is the shore-contractor list for one deck.
 *
 * `null` on a dimension means "no filter on this dimension".
 */
data class DueFilters(
    val deckId: String? = null,
    val zoneId: String? = null,
    val categoryId: String? = null,
    val group: EquipmentGroup? = null,
    val performedBy: PerformedBy? = null,
    val condition: ConditionGrade? = null,
) {
    val activeCount: Int
        get() = listOf(deckId, zoneId, categoryId, group, performedBy, condition).count { it != null }

    val isActive: Boolean get() = activeCount > 0
}

/** One line of the work list — §12: symbol, tag, deck, task title, due date + delta, performer. */
data class DueRow(
    val instanceId: String,
    val equipmentId: String,
    val tag: String,
    val symbolKey: String,
    val deckId: String?,
    val deckShortName: String,
    val zoneId: String?,
    val taskKey: String,
    val taskTitle: LocalisedText,
    val equipmentTypeName: LocalisedText,
    /** Epoch-days. */
    val dueDate: Long,
    /** `dueDate - today`, signed: negative is late, positive is time in hand. */
    val dayDelta: Long,
    val status: TaskStatus,
    val performedBy: PerformedBy,
    val condition: ConditionGrade,
    val group: EquipmentGroup,
    val segment: DueSegment,
)

/** The values the filter chips can offer, derived from what the vessel actually holds. */
data class DueFilterOptions(
    val decks: List<FilterOption> = emptyList(),
    val zones: List<FilterOption> = emptyList(),
    val categories: List<FilterOption> = emptyList(),
    val groups: List<EquipmentGroup> = emptyList(),
    val performers: List<PerformedBy> = emptyList(),
    val conditions: List<ConditionGrade> = emptyList(),
)

data class FilterOption(val id: String, val label: String)

/**
 * "Survey prep" — §12. Given the Safety Equipment Certificate expiry, everything that falls due
 * before it, split by whether the ship can do the work itself or a shore provider must attend.
 */
data class SurveyPrepState(
    /** Epoch-days. */
    val certExpiry: Long,
    val daysToExpiry: Long,
    val shipStaff: List<DueRow> = emptyList(),
    val shoreProvider: List<DueRow> = emptyList(),
    /** Distinct shore-service task titles with their counts — the shopping list for the agent. */
    val shoppingList: List<ShoreServiceItem> = emptyList(),
)

/** One line of the shore-service shopping list: a task title and how many items need it. */
data class ShoreServiceItem(
    val taskKey: String,
    val title: LocalisedText,
    val performedBy: PerformedBy,
    val count: Int,
)

/** Everything the Due work list renders — §12. */
data class DueUiState(
    val loading: Boolean = true,
    val vesselName: String = "",
    val vesselImoNumber: String? = null,
    /** Epoch-days; `null` when the vessel record has no Safety Equipment Certificate expiry. */
    val certExpiry: Long? = null,
    /** Epoch-day the buckets were computed against. */
    val today: Long = 0L,
    val segment: DueSegment = DueSegment.OVERDUE,
    val filters: DueFilters = DueFilters(),
    val options: DueFilterOptions = DueFilterOptions(),
    /** Filtered counts per segment — what the chips show. */
    val counts: Map<DueSegment, Int> = emptyMap(),
    /** The filtered rows of the selected segment. */
    val rows: List<DueRow> = emptyList(),
    val surveyPrepEnabled: Boolean = false,
    val surveyPrep: SurveyPrepState? = null,
) {
    val hasVessel: Boolean get() = vesselName.isNotEmpty()

    fun countOf(segment: DueSegment): Int = counts[segment] ?: 0
}

/** Ship's staff can do it; anyone else means the item goes on the shore-service list — §11.4. */
val PerformedBy.isShipStaff: Boolean
    get() = this == PerformedBy.SHIP_STAFF || this == PerformedBy.SHIP_STAFF_TRAINED
