package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade

/** The five gradable conditions in display order (NOT_CHECKED is a state, not a choice). */
val GradableConditions: List<ConditionGrade> = listOf(
    ConditionGrade.GOOD,
    ConditionGrade.ACCEPTABLE,
    ConditionGrade.MONITOR,
    ConditionGrade.DEFECTIVE,
    ConditionGrade.OUT_OF_SERVICE,
)

fun conditionIcon(grade: ConditionGrade): ImageVector = when (grade) {
    ConditionGrade.GOOD -> Icons.Filled.CheckCircle
    ConditionGrade.ACCEPTABLE -> Icons.Filled.ThumbUp
    ConditionGrade.MONITOR -> Icons.Filled.Visibility
    ConditionGrade.DEFECTIVE -> Icons.Filled.ErrorOutline
    ConditionGrade.OUT_OF_SERVICE -> Icons.Filled.Block
    ConditionGrade.NOT_CHECKED -> Icons.Filled.Visibility
}

/**
 * The ONLY control that sets a condition grade — §7.3 and DESIGN_OVERHAUL
 * rule 5. Five 56dp chips, icon + text, semantic colour, selected state.
 */
@Composable
fun ConditionChipRow(
    selected: ConditionGrade?,
    onSelect: (ConditionGrade) -> Unit,
    modifier: Modifier = Modifier,
    labels: ConditionLabels = ConditionLabels(),
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        GradableConditions.forEach { grade ->
            val colour = ConditionColors.of(grade)
            val isSelected = grade == selected
            val container = if (isSelected) colour else colour.copy(alpha = 0.12f)
            val content = if (isSelected) Color.White else colour
            Surface(
                onClick = { onSelect(grade) },
                enabled = enabled,
                shape = RoundedCornerShape(Dimens.ChipCorner),
                color = container,
                border = BorderStroke(if (isSelected) 0.dp else 1.dp, colour.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.TouchTargetPrimary)
                    .semantics(mergeDescendants = true) {
                        this.selected = isSelected
                        this.role = Role.RadioButton
                    },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = Dimens.SpacingXs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = conditionIcon(grade),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = labels.of(grade),
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A small filled dot for lists and markers. */
@Composable
fun ConditionDot(grade: ConditionGrade, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(50),
        color = ConditionColors.of(grade),
        content = {},
    )
}
