package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus

/**
 * Status reads at a glance — DESIGN_OVERHAUL rule 6. The text carries the
 * meaning; colour reinforces it. Tinted container with a solid text colour so
 * it stays legible in all three themes.
 */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 28.dp),
        shape = RoundedCornerShape(Dimens.ChipCorner),
        color = if (filled) color else color.copy(alpha = 0.16f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) Color.White else color,
            modifier = Modifier.padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
        )
    }
}

@Composable
fun TaskStatusChip(status: TaskStatus, text: String, modifier: Modifier = Modifier) =
    StatusChip(text = text, color = ConditionColors.of(status), modifier = modifier, filled = status == TaskStatus.OVERDUE)

@Composable
fun SeverityChip(severity: Severity, text: String, modifier: Modifier = Modifier) =
    StatusChip(text = text, color = ConditionColors.of(severity), modifier = modifier, filled = severity == Severity.CRITICAL_DETAINABLE)

@Composable
fun DeficiencyStatusChip(status: DeficiencyStatus, text: String, modifier: Modifier = Modifier) {
    val color = when (status) {
        DeficiencyStatus.OPEN -> ConditionColors.Defective
        DeficiencyStatus.IN_PROGRESS -> ConditionColors.Monitor
        DeficiencyStatus.CLOSED -> ConditionColors.Good
        DeficiencyStatus.DEFERRED_TO_OFFICE -> ConditionColors.NotChecked
    }
    StatusChip(text = text, color = color, modifier = modifier)
}

/**
 * A due-date delta chip: negative = late (filled red), 0 = today (amber),
 * positive = ahead (neutral, amber inside [warnWithinDays]).
 */
@Composable
fun DueDeltaChip(daysUntilDue: Long, text: String, modifier: Modifier = Modifier, warnWithinDays: Long = 30) {
    val color = when {
        daysUntilDue < 0 -> ConditionColors.OutOfService
        daysUntilDue == 0L -> ConditionColors.Defective
        daysUntilDue <= warnWithinDays -> ConditionColors.Monitor
        else -> ConditionColors.NotChecked
    }
    StatusChip(text = text, color = color, modifier = modifier, filled = daysUntilDue < 0)
}
