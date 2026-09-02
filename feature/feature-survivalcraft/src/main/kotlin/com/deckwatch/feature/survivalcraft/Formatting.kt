package com.deckwatch.feature.survivalcraft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.TaskStatus

/** Turkish when the device is set to Turkish and a Turkish string exists; English otherwise — C8. */
@Composable
@ReadOnlyComposable
internal fun localised(english: String, turkish: String): String =
    if (Locale.current.language == "tr" && turkish.isNotBlank()) turkish else english

/** ISO-8601 (`2026-03-12`) or an em dash when there is no date — §6: dates are epoch-days. */
@Composable
@ReadOnlyComposable
internal fun formatDate(epochDay: Long?): String =
    epochDay?.let(Dates::formatIso) ?: stringResource(R.string.sc_not_set)

/** "12 d late" / "Due today" / "in 34 d" — the text carries the meaning, colour only reinforces. */
@Composable
@ReadOnlyComposable
internal fun dueDeltaText(dueDate: Long?, todayEpochDay: Long): String {
    if (dueDate == null) return stringResource(R.string.sc_due_none)
    val delta = dueDate - todayEpochDay
    return when {
        delta < 0 -> stringResource(R.string.sc_due_late, -delta)
        delta == 0L -> stringResource(R.string.sc_due_today)
        else -> stringResource(R.string.sc_due_in, delta)
    }
}

@Composable
@ReadOnlyComposable
internal fun conditionLabel(grade: ConditionGrade): String = stringResource(
    when (grade) {
        ConditionGrade.GOOD -> R.string.sc_condition_good
        ConditionGrade.ACCEPTABLE -> R.string.sc_condition_acceptable
        ConditionGrade.MONITOR -> R.string.sc_condition_monitor
        ConditionGrade.DEFECTIVE -> R.string.sc_condition_defective
        ConditionGrade.OUT_OF_SERVICE -> R.string.sc_condition_out_of_service
        ConditionGrade.NOT_CHECKED -> R.string.sc_condition_not_checked
    },
)

@Composable
@ReadOnlyComposable
internal fun taskStatusLabel(status: TaskStatus): String = stringResource(
    when (status) {
        TaskStatus.PENDING -> R.string.sc_task_pending
        TaskStatus.DUE_SOON -> R.string.sc_task_due_soon
        TaskStatus.OVERDUE -> R.string.sc_task_overdue
        TaskStatus.DONE -> R.string.sc_task_done
        TaskStatus.SKIPPED -> R.string.sc_task_skipped
        TaskStatus.NOT_APPLICABLE -> R.string.sc_task_not_applicable
    },
)

@Composable
@ReadOnlyComposable
internal fun taskGroupLabel(group: TaskGroup): String = stringResource(
    when (group) {
        TaskGroup.WEEKLY -> R.string.sc_group_weekly
        TaskGroup.MONTHLY -> R.string.sc_group_monthly
        TaskGroup.ANNUAL -> R.string.sc_group_annual
        TaskGroup.FIVE_YEARLY -> R.string.sc_group_five_yearly
        TaskGroup.OTHER -> R.string.sc_group_other
    },
)

/**
 * Localised label for an inventory row: template rows come from strings.xml, rows the officer
 * added carry their own label.
 */
@Composable
@ReadOnlyComposable
internal fun inventoryItemLabel(key: String, fallback: String?): String {
    val res = InventoryLabels.resFor(key)
    return if (res != null) stringResource(res) else fallback ?: key
}
