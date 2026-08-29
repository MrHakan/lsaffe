package com.deckwatch.feature.vessel.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.vessel.R

/**
 * The eight fixed tint swatches offered for decks, zones and categories.
 *
 * Fixed rather than free — a colour picker on a wet deck with gloves is a bad control, and a
 * small fixed palette keeps a stack of twenty decks legible. Stored as ARGB ints (§6.2, §6.4).
 */
data class Swatch(val argb: Int, @param:StringRes val labelRes: Int) {
    val color: Color get() = Color(argb)
}

object Swatches {

    val All: List<Swatch> = listOf(
        Swatch(0xFF5C6779.toInt(), R.string.colour_slate),
        Swatch(0xFF283449.toInt(), R.string.colour_navy),
        Swatch(0xFF1F7A75.toInt(), R.string.colour_teal),
        Swatch(0xFF1B873F.toInt(), R.string.colour_green),
        Swatch(0xFFE8A317.toInt(), R.string.colour_amber),
        Swatch(0xFFE5661B.toInt(), R.string.colour_orange),
        Swatch(0xFFC2261B.toInt(), R.string.colour_red),
        Swatch(0xFF6A4C9C.toInt(), R.string.colour_violet),
    )

    val Default: Swatch = All.first()

    /** The swatch for a stored ARGB value, or [Default] when the value is unknown or absent. */
    fun of(argb: Int?): Swatch = All.firstOrNull { it.argb == argb } ?: Default
}

/**
 * A row of the eight swatches. Each target is [Dimens.TouchTargetMin] even though the painted
 * dot is smaller, so it stays glove-usable (C6).
 */
@Composable
fun SwatchRow(
    selectedArgb: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (swatch in Swatches.All) {
            val selected = swatch.argb == selectedArgb
            val label = stringResource(swatch.labelRes)
            Box(
                modifier = Modifier
                    .size(Dimens.TouchTargetMin)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(swatch.argb) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(SWATCH_DOT)
                        .clip(CircleShape)
                        .background(swatch.color)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(SWATCH_CHECK),
                        )
                    }
                }
            }
        }
    }
}

private val SWATCH_DOT = 30.dp
private val SWATCH_CHECK = 18.dp
