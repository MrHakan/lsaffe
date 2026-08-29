package com.deckwatch.feature.equipment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.components.RegulationCardLabels
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.StatusFlag
import com.deckwatch.core.model.TaskStatus

/**
 * Bilingual display helpers — C8.
 *
 * Regulatory *content* stays in English (it is quoted terminology); only the app's own chrome and
 * the catalogue's translated name/label fields switch language.
 */

/** Turkish when the device is set to Turkish and a Turkish string exists; English otherwise. */
@Composable
@ReadOnlyComposable
internal fun localised(english: String, turkish: String): String =
    if (Locale.current.language == "tr" && turkish.isNotBlank()) turkish else english

/** ISO-8601 (`2026-03-12`) or an em dash when there is no date — §6: dates are epoch-days. */
@Composable
@ReadOnlyComposable
internal fun formatDate(epochDay: Long?): String =
    epochDay?.let(Dates::formatIso) ?: stringResource(R.string.attr_not_set)

/** "12 days overdue" / "Due today" / "in 34 days" — the colour-coded countdown of §7.4 peek. */
@Composable
@ReadOnlyComposable
internal fun dueDeltaText(dueDate: Long?, todayEpochDay: Long): String {
    if (dueDate == null) return stringResource(R.string.equip_next_due_none)
    val delta = dueDate - todayEpochDay
    return when {
        delta < -1 -> stringResource(R.string.equip_due_overdue, -delta)
        delta == -1L -> stringResource(R.string.equip_due_overdue_one)
        delta == 0L -> stringResource(R.string.equip_due_today)
        delta == 1L -> stringResource(R.string.equip_due_in_one_day)
        else -> stringResource(R.string.equip_due_in_days, delta)
    }
}

/**
 * Countdown colour: past due is the out-of-service red, inside the default 30-day lead time is
 * amber, further out is neutral — the same semantics the Due tab uses (§11.1 (4), §14).
 */
internal fun dueColor(dueDate: Long?, todayEpochDay: Long): Color = when {
    dueDate == null -> ConditionColors.NotChecked
    dueDate < todayEpochDay -> ConditionColors.OutOfService
    dueDate - todayEpochDay <= DUE_SOON_LEAD_DAYS -> ConditionColors.Monitor
    else -> ConditionColors.Good
}

/** The user's due lead-time setting default — §11.1 (4). */
private const val DUE_SOON_LEAD_DAYS = 30

@Composable
@ReadOnlyComposable
internal fun conditionLabel(grade: ConditionGrade): String = stringResource(
    when (grade) {
        ConditionGrade.GOOD -> R.string.equip_condition_good
        ConditionGrade.ACCEPTABLE -> R.string.equip_condition_acceptable
        ConditionGrade.MONITOR -> R.string.equip_condition_monitor
        ConditionGrade.DEFECTIVE -> R.string.equip_condition_defective
        ConditionGrade.OUT_OF_SERVICE -> R.string.equip_condition_out_of_service
        ConditionGrade.NOT_CHECKED -> R.string.equip_condition_not_checked
    },
)

@Composable
@ReadOnlyComposable
internal fun taskStatusLabel(status: TaskStatus): String = stringResource(
    when (status) {
        TaskStatus.PENDING -> R.string.equip_task_status_pending
        TaskStatus.DUE_SOON -> R.string.equip_task_status_due_soon
        TaskStatus.OVERDUE -> R.string.equip_task_status_overdue
        TaskStatus.DONE -> R.string.equip_task_status_done
        TaskStatus.SKIPPED -> R.string.equip_task_status_skipped
        TaskStatus.NOT_APPLICABLE -> R.string.equip_task_status_not_applicable
    },
)

@Composable
@ReadOnlyComposable
internal fun severityLabel(severity: Severity): String = stringResource(
    when (severity) {
        Severity.OBSERVATION -> R.string.equip_severity_observation
        Severity.MINOR -> R.string.equip_severity_minor
        Severity.MAJOR -> R.string.equip_severity_major
        Severity.CRITICAL_DETAINABLE -> R.string.equip_severity_critical
    },
)

@Composable
@ReadOnlyComposable
internal fun groupLabel(group: EquipmentGroup): String = stringResource(
    when (group) {
        EquipmentGroup.LSA -> R.string.add_group_lsa
        EquipmentGroup.FFE -> R.string.add_group_ffe
        EquipmentGroup.EMERGENCY_ESCAPE -> R.string.add_group_emergency
        EquipmentGroup.MACHINERY_CONTROLS -> R.string.add_group_machinery
        EquipmentGroup.SIGNAGE -> R.string.add_group_signage
        EquipmentGroup.OTHER -> R.string.add_group_other
    },
)

/**
 * Service status is shown verbatim from the enum: `LANDED_ASHORE` and `AWAITING_SPARE` are terms of
 * art in the register and are not translated until the settings module owns that vocabulary.
 */
internal fun statusLabel(status: StatusFlag): String =
    status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Localised labels for the shared [com.deckwatch.core.designsystem.components.RegulationCardView] (§8.2). */
@Composable
@ReadOnlyComposable
internal fun regulationCardLabels(): RegulationCardLabels = RegulationCardLabels(
    what = stringResource(R.string.reg_label_what),
    howOften = stringResource(R.string.reg_label_how_often),
    byWhom = stringResource(R.string.reg_label_by_whom),
    evidence = stringResource(R.string.reg_label_evidence),
    flagNotes = stringResource(R.string.reg_label_flag_notes),
    appliesTo = stringResource(R.string.reg_label_applies_to),
    verifyStrip = stringResource(R.string.reg_label_verify),
    revisionPrefix = stringResource(R.string.reg_label_captured),
)
