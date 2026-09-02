@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.deckview.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.feature.vessel.common.DeckPlanThumbnail
import com.deckwatch.feature.deckview.DeckViewMode
import com.deckwatch.feature.deckview.R
import com.deckwatch.feature.deckview.geometry.IsoProjection
import com.deckwatch.feature.deckview.render.DeckTransformState
import kotlin.math.roundToInt

/** The §7.1 mode switch: stack, one deck, or the graphics-free list. */
@Composable
fun DeckModeControl(
    mode: DeckViewMode,
    onModeChange: (DeckViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        DeckViewMode.STACK to stringResource(R.string.deckview_mode_stack),
        DeckViewMode.DECK to stringResource(R.string.deckview_mode_deck),
        DeckViewMode.LIST to stringResource(R.string.deckview_mode_list),
    )
    val icons = listOf(Icons.Filled.Layers, Icons.Filled.Map, Icons.AutoMirrored.Filled.List)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                icon = { Icon(icons[index], contentDescription = null) },
                label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * The view settings of §7.2, kept out of the top bar: the fan spread slider (0.5×–3×), the isometric
 * angle (0°–35°, where 0° is the flat plan) and the placement grid.
 *
 * The spread and angle values are read here and nowhere else, so dragging either recomposes only
 * this sheet and never the canvas.
 */
@Composable
@Suppress("LongParameterList") // One settings surface with four independent controls.
fun ViewSettingsSheet(
    transform: DeckTransformState,
    isoAngleDeg: Float,
    gridSnapEnabled: Boolean,
    showGrid: Boolean,
    onIsoAngleChange: (Float) -> Unit,
    onGridSnapChange: (Boolean) -> Unit,
    onShowGridChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var spread by remember { mutableFloatStateOf(transform.spread) }
    var angle by remember { mutableFloatStateOf(isoAngleDeg) }
    val spreadLabel = stringResource(R.string.deckview_spread_label)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpacingL)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        ) {
            Text(
                text = stringResource(R.string.deckview_view_settings),
                style = MaterialTheme.typography.titleMedium,
            )

            SliderRow(
                label = stringResource(R.string.deckview_spread_label),
                value = stringResource(R.string.deckview_spread_value, spread),
            ) {
                Slider(
                    value = spread,
                    onValueChange = {
                        spread = it
                        transform.updateSpread(it)
                    },
                    valueRange = IsoProjection.MIN_SPREAD..IsoProjection.MAX_SPREAD,
                    modifier = Modifier.semantics { contentDescription = spreadLabel },
                )
            }

            SliderRow(
                label = stringResource(R.string.deckview_angle_label),
                value = stringResource(R.string.deckview_angle_value, angle.roundToInt()),
            ) {
                Slider(
                    value = angle,
                    onValueChange = { angle = it },
                    onValueChangeFinished = { onIsoAngleChange(angle) },
                    valueRange = IsoProjection.MIN_ANGLE_DEG..IsoProjection.MAX_ANGLE_DEG,
                )
            }

            ToggleRow(
                label = stringResource(R.string.deckview_show_grid),
                checked = showGrid,
                onCheckedChange = onShowGridChange,
            )
            ToggleRow(
                label = stringResource(R.string.deckview_grid_snap),
                checked = gridSnapEnabled,
                onCheckedChange = onGridSnapChange,
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
        slider()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The six §6.3 presets, offered straight in the first-run empty state (§14, DESIGN_OVERHAUL 7). */
@Composable
fun PresetPickerRow(
    presets: List<PlanPreset>,
    presetLabel: (PlanPreset) -> String,
    onPick: (PlanPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // A scrolling rail rather than a stack of buttons: six full-width buttons plus the empty state's
    // icon and copy do not fit a small phone at 200 % font scale (§14).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpacingL)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        for (preset in presets) {
            OutlinedButton(
                onClick = { onPick(preset) },
                contentPadding = PaddingValues(Dimens.SpacingS),
                modifier = Modifier
                    .width(PRESET_TILE_WIDTH)
                    .heightIn(min = PRESET_TILE_HEIGHT),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DeckPlanThumbnail(
                        plan = preset.plan,
                        fill = scheme.surfaceContainerHighest,
                        stroke = scheme.outline,
                        width = THUMBNAIL_WIDTH,
                        height = THUMBNAIL_HEIGHT,
                    )
                    Text(
                        text = presetLabel(preset),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = Dimens.SpacingXs),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val THUMBNAIL_WIDTH = 32.dp
private val THUMBNAIL_HEIGHT = 44.dp
private val PRESET_TILE_WIDTH = 104.dp
private val PRESET_TILE_HEIGHT = 120.dp
