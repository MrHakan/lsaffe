package com.deckwatch.feature.settings.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.theme.Dimens

/**
 * The four row shapes the §18 settings screen is built from.
 *
 * All of them sit on `heightIn(min = 56.dp)` — DESIGN_OVERHAUL rule 3 asks for 56dp rows and this
 * screen is the one an officer uses with gloves on, standing up. Labels are never icon-only, and
 * every control carries the row's own label as its accessibility name so TalkBack does not read a
 * bare "switch, on".
 */

/** Label + one-line description + a trailing switch. The whole row toggles. */
@Composable
internal fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    DeckWatchListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                // Named so TalkBack reads "Daily reminder, on" and not a bare "switch, on". The
                // toggle action itself is left intact — this merges into the switch's semantics
                // rather than replacing them.
                modifier = Modifier.semantics { contentDescription = title },
            )
        },
    )
}

/** Label + a segmented control underneath. Used for ≤4 mutually exclusive options. */
@Composable
internal fun <T> SegmentedSettingRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingS)
                .heightIn(min = Dimens.TouchTargetMin),
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(text = label(option), maxLines = 1)
                }
            }
        }
    }
}

/** Label + a row of filter chips. Used when there are more options than a segmented row can hold. */
@Composable
internal fun <T> ChipSettingRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option), maxLines = 1) },
                    modifier = Modifier
                        .padding(end = Dimens.SpacingS)
                        .heightIn(min = CHIP_MIN_HEIGHT),
                )
            }
        }
    }
}

/**
 * Label + current value + a slider.
 *
 * The value is committed on every change rather than on release: DataStore writes are cheap, and an
 * officer who drags and then locks the screen has still saved what they chose.
 */
@Composable
internal fun SliderSettingRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueLabel, style = MaterialTheme.typography.titleMedium)
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(min = Dimens.TouchTargetMin)
                // Named, not replaced: the slider keeps its own range and drag semantics, which is
                // what makes it operable from TalkBack's adjust gestures.
                .semantics { contentDescription = title },
        )
    }
}

/** DESIGN_OVERHAUL rule 3: chips are at least 40dp tall. */
private val CHIP_MIN_HEIGHT = Dimens.TouchTargetMin
