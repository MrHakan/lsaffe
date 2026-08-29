package com.deckwatch.feature.equipment.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.feature.equipment.conditionLabel

/**
 * The quick-action condition control — §7.3, the single most-used control in the app.
 *
 * Five chips, each [Dimens.TouchTargetPrimary] tall with **both icon and text** (C6: this is used on
 * deck, in wind, sometimes with gloves), colour-coded per §6.9. One tap writes the grade; the
 * caller does the writing, the haptic and the follow-on deficiency form.
 *
 * `NOT_CHECKED` is not offered: it is the initial state, not a grade an officer awards.
 */
@Composable
internal fun ConditionChipRow(
    selected: ConditionGrade,
    onGrade: (ConditionGrade) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        GRADES.forEach { grade ->
            ConditionChip(
                grade = grade,
                selected = grade == selected,
                enabled = enabled,
                onClick = { onGrade(grade) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConditionChip(
    grade: ConditionGrade,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = ConditionColors.of(grade)
    val shape = RoundedCornerShape(Dimens.ChipCorner)
    val label = conditionLabel(grade)
    val content = if (selected) onColorFor(accent) else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .height(Dimens.TouchTargetPrimary)
            .clip(shape)
            .background(if (selected) accent else Color.Transparent)
            .border(width = if (selected) 0.dp else BorderWidth, color = accent, shape = shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Dimens.SpacingXs, vertical = Dimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = iconFor(grade),
            contentDescription = null,
            tint = if (selected) content else accent,
            modifier = Modifier.height(IconSize),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun iconFor(grade: ConditionGrade): ImageVector = when (grade) {
    ConditionGrade.GOOD -> Icons.Filled.CheckCircle
    ConditionGrade.ACCEPTABLE -> Icons.Filled.Check
    ConditionGrade.MONITOR -> Icons.Filled.Warning
    ConditionGrade.DEFECTIVE -> Icons.Filled.Error
    ConditionGrade.OUT_OF_SERVICE -> Icons.Filled.Block
    ConditionGrade.NOT_CHECKED -> Icons.Filled.Check
}

/** Black or white on the fixed condition colour, whichever keeps the 4.5:1 contrast of §14. */
private fun onColorFor(background: Color): Color =
    if (background.luminance() > LIGHT_GROUND_THRESHOLD) Color.Black else Color.White

private const val LIGHT_GROUND_THRESHOLD = 0.45f
private val BorderWidth = 1.dp
private val IconSize = 20.dp

/** The five grades the officer awards, in the order §7.3 lists them. */
private val GRADES = listOf(
    ConditionGrade.GOOD,
    ConditionGrade.ACCEPTABLE,
    ConditionGrade.MONITOR,
    ConditionGrade.DEFECTIVE,
    ConditionGrade.OUT_OF_SERVICE,
)
