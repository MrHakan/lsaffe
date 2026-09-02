package com.deckwatch.feature.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import com.deckwatch.core.designsystem.components.ConditionLabels
import com.deckwatch.core.designsystem.components.DateFieldLabels
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.Severity

/**
 * Shared chrome for the inspection feature: the bilingual enum labels of C8 and the tag monospace of
 * §14. Everything visual — top bar, empty state, condition chips, status chips, date input, list
 * rows — comes from `core-designsystem/components`; this file only feeds it localised words
 * (DESIGN_OVERHAUL, "definition of done").
 */

/** True when the device is running Turkish, so authored `*Tr` content is preferred — C8. */
@Composable
@ReadOnlyComposable
fun isTurkishLocale(): Boolean = Locale.current.language.equals("tr", ignoreCase = true)

@Composable
@ReadOnlyComposable
fun labelOf(segment: DueSegment): String = stringResource(
    when (segment) {
        DueSegment.OVERDUE -> R.string.due_segment_overdue
        DueSegment.THIS_WEEK -> R.string.due_segment_week
        DueSegment.THIS_MONTH -> R.string.due_segment_month
        DueSegment.BEFORE_SURVEY -> R.string.due_segment_survey
        DueSegment.PLANNED -> R.string.due_segment_planned
    },
)

@Composable
@ReadOnlyComposable
fun labelOf(performedBy: PerformedBy): String = stringResource(
    when (performedBy) {
        PerformedBy.SHIP_STAFF -> R.string.insp_performed_by_ship_staff
        PerformedBy.SHIP_STAFF_TRAINED -> R.string.insp_performed_by_ship_staff_trained
        PerformedBy.AUTHORISED_SERVICE_PROVIDER -> R.string.insp_performed_by_service_provider
        PerformedBy.MANUFACTURER -> R.string.insp_performed_by_manufacturer
        PerformedBy.RO_SURVEYOR_ATTENDING -> R.string.insp_performed_by_ro_surveyor
        PerformedBy.SHORE_FACILITY -> R.string.insp_performed_by_shore_facility
    },
)

@Composable
@ReadOnlyComposable
fun labelOf(condition: ConditionGrade): String = stringResource(
    when (condition) {
        ConditionGrade.GOOD -> R.string.insp_condition_good
        ConditionGrade.ACCEPTABLE -> R.string.insp_condition_acceptable
        ConditionGrade.MONITOR -> R.string.insp_condition_monitor
        ConditionGrade.DEFECTIVE -> R.string.insp_condition_defective
        ConditionGrade.OUT_OF_SERVICE -> R.string.insp_condition_out_of_service
        ConditionGrade.NOT_CHECKED -> R.string.insp_condition_not_checked
    },
)

@Composable
@ReadOnlyComposable
fun labelOf(group: EquipmentGroup): String = stringResource(
    when (group) {
        EquipmentGroup.LSA -> R.string.insp_group_lsa
        EquipmentGroup.FFE -> R.string.insp_group_ffe
        EquipmentGroup.EMERGENCY_ESCAPE -> R.string.insp_group_emergency_escape
        EquipmentGroup.MACHINERY_CONTROLS -> R.string.insp_group_machinery
        EquipmentGroup.SIGNAGE -> R.string.insp_group_signage
        EquipmentGroup.OTHER -> R.string.insp_group_other
    },
)

@Composable
@ReadOnlyComposable
fun labelOf(severity: Severity): String = stringResource(
    when (severity) {
        Severity.OBSERVATION -> R.string.insp_severity_observation
        Severity.MINOR -> R.string.insp_severity_minor
        Severity.MAJOR -> R.string.insp_severity_major
        Severity.CRITICAL_DETAINABLE -> R.string.insp_severity_critical
    },
)

@Composable
@ReadOnlyComposable
fun labelOf(status: DeficiencyStatus): String = stringResource(
    when (status) {
        DeficiencyStatus.OPEN -> R.string.deficiency_status_open
        DeficiencyStatus.IN_PROGRESS -> R.string.deficiency_status_in_progress
        DeficiencyStatus.CLOSED -> R.string.deficiency_status_closed
        DeficiencyStatus.DEFERRED_TO_OFFICE -> R.string.deficiency_status_deferred
    },
)

/** The signed day delta as words: "12 d late" / "due today" / "in 30 d" — §12. */
@Composable
@ReadOnlyComposable
fun deltaLabel(dayDelta: Long): String = when {
    dayDelta < 0 -> stringResource(R.string.due_delta_late, -dayDelta)
    dayDelta == 0L -> stringResource(R.string.due_delta_today)
    else -> stringResource(R.string.due_delta_ahead, dayDelta)
}

/** The module's words for the shared five-grade control — DESIGN_OVERHAUL rule 5. */
@Composable
@ReadOnlyComposable
fun conditionLabels(): ConditionLabels = ConditionLabels(
    good = stringResource(R.string.insp_condition_good),
    acceptable = stringResource(R.string.insp_condition_acceptable),
    monitor = stringResource(R.string.insp_condition_monitor),
    defective = stringResource(R.string.insp_condition_defective),
    outOfService = stringResource(R.string.insp_condition_out_of_service),
    notChecked = stringResource(R.string.insp_condition_not_checked),
)

/** The module's words for the shared date picker field — DESIGN_OVERHAUL rule 4. */
@Composable
@ReadOnlyComposable
fun dateFieldLabels(): DateFieldLabels = DateFieldLabels(
    pick = stringResource(R.string.insp_date_pick),
    clear = stringResource(R.string.insp_date_clear),
    confirm = stringResource(R.string.insp_date_ok),
    cancel = stringResource(R.string.insp_action_cancel),
)

/** The ship's own identifier, in the monospace that disambiguates 0/O and 1/l/I — §14. */
@Composable
fun TagText(tag: String, modifier: Modifier = Modifier) {
    Text(
        text = tag,
        style = tagTextStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * One dimension of the Due tab's filter sheet — §12. Selecting a value narrows the list; the "All"
 * entry clears just that dimension, so the dimensions stay independent and combinable.
 */
@Composable
fun <T> FilterDropdownChip(
    label: String,
    selectedLabel: String?,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterChip(
            selected = selectedLabel != null,
            onClick = { expanded = true },
            enabled = options.isNotEmpty(),
            label = { Text(selectedLabel ?: label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            shape = RoundedCornerShape(Dimens.ChipCorner),
            colors = FilterChipDefaults.filterChipColors(),
            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.due_filter_all)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                val text = optionLabel(option)
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A left/right swipe backdrop: a coloured panel with its action word on the side being revealed.
 * It fills whatever height the row settled at, so a 200 % font scale grows the panel with the row
 * instead of clipping it.
 */
@Composable
fun SwipeActionBackground(
    text: String,
    color: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowCompact)
            .background(color)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignment == Alignment.Start) Arrangement.Start else Arrangement.End,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
