package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.plateTextStyle
import com.deckwatch.core.designsystem.theme.tagTextStyle

/**
 * The status spine: a full-height colour bar down the leading edge of a row — §14.
 *
 * It replaces the condition dot on scanning surfaces, and the reason is the use case rather than
 * taste. A 10 dp dot at the trailing edge is the smallest element on the row, at the end of the
 * scan path, under the thumb, and invisible through a visor in sunlight. A bar at the leading edge
 * is the first thing the eye meets going down a list, cannot be covered by the hand holding the
 * phone, and reads at arm's length.
 *
 * It is the same device a fire control plan uses: on a plan, colour *is* the information. So the
 * rule this design follows everywhere is that saturated colour appears only where it encodes state.
 *
 * In the bridge theme every hue collapses towards red, and the spine still works: its presence
 * marks a row that carries state even when its hue no longer distinguishes which.
 *
 * @param contentDescription what the colour means, for a reader that cannot see it. Pass null only
 *   when the surrounding row already carries the same state in text — a severity chip beside the
 *   spine, or a row-level description. Colour is never the only carrier of meaning; this parameter
 *   is where that is decided, once, rather than in each list.
 */
@Composable
fun StatusSpine(
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.SpineWidth)
            .clip(RoundedCornerShape(Dimens.SpineWidth / 2))
            .background(color)
            .clearAndSetSemantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    )
}

/**
 * An equipment tag, set the way it is set on the ship — §14.
 *
 * A tag is stencilled or engraved on a plate beside the equipment, so it is drawn as one here:
 * monospace, letterspaced, on a recessed ground with a hairline. The plate is not decoration — it
 * is what makes `FE-UD-01` read as an identifier rather than as a word, which matters because
 * these get read aloud and cross-checked against paperwork.
 */
@Composable
fun TagPlate(tag: String, modifier: Modifier = Modifier) {
    Text(
        text = tag,
        style = tagTextStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.PlateCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = Dimens.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(Dimens.PlateCorner),
            )
            .padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
    )
}

/**
 * A section heading in the app's signage voice: uppercase, tracked, on a hairline rule.
 *
 * The app carries no bundled typeface — it is an offline app and a font file is weight for no
 * gain — so the display personality comes from treatment rather than from a bought face. Wide
 * tracking and capitals on a rule is the vernacular of deck markings and ship's plates, and it
 * separates a heading from body text at a glance without another type size.
 *
 * @param trailing optional control at the end of the rule, such as a count or an action.
 */
@Composable
fun PlateHeading(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = plateTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Dimens.SpacingS),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = Dimens.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(modifier = Modifier.padding(start = Dimens.SpacingS)) { trailing() }
    }
}

/** Leading inset for content that sits beside a [StatusSpine], so text lines up down the list. */
val SpineInset = Dimens.SpineWidth + 12.dp
