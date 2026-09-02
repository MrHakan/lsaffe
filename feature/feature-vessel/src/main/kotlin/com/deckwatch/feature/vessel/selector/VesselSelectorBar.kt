package com.deckwatch.feature.vessel.selector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.R

/**
 * The persistent vessel selector of §5: the active vessel's name plus a switcher, compact enough
 * to sit in a top app bar.
 *
 * Stateless by design — pass [vessels], [active] and [onSelect] and it renders anywhere, including
 * a preview or a UI test with no DI graph. [VesselSelector] is the convenience that wires it to
 * its own view model.
 *
 * Menu rows are 56dp: this is switched at sea, with gloves on (DESIGN_OVERHAUL rule 3). The active
 * vessel is ticked *and* labelled, and the last row goes straight to adding a new vessel through
 * [onAddVessel].
 */
@Composable
fun VesselSelectorBar(
    vessels: List<Vessel> = emptyList(),
    active: Vessel? = null,
    onSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onAddVessel: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val switchLabel = stringResource(R.string.vessel_selector_switch)
    val activeLabel = stringResource(R.string.vessel_manager_active)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimens.TouchTargetMin)
                .clickable(
                    role = Role.DropdownList,
                    onClickLabel = switchLabel,
                    onClick = { expanded = true },
                )
                .padding(horizontal = Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.vessel_selector_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = active?.name ?: stringResource(R.string.vessel_selector_none),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = NAME_MAX_WIDTH),
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = switchLabel,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = MENU_MIN_WIDTH),
        ) {
            for (vessel in vessels) {
                val isActive = vessel.id == active?.id
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary),
                    text = {
                        Text(
                            text = vessel.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = activeLabel,
                                tint = ConditionColors.Good,
                                modifier = Modifier.size(CHECK_SIZE),
                            )
                        }
                    },
                    // The tick is reinforced by the word, so colour is never the only signal.
                    trailingIcon = {
                        if (isActive) {
                            Text(
                                text = activeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = ConditionColors.Good,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(vessel.id)
                    },
                )
            }
            if (vessels.isNotEmpty()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary),
                text = { Text(text = stringResource(R.string.vessel_manager_add)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(CHECK_SIZE),
                    )
                },
                onClick = {
                    expanded = false
                    onAddVessel()
                },
            )
        }
    }
}

/** [VesselSelectorBar] wired to its own view model — the drop-in for an app bar. */
@Composable
fun VesselSelector(
    modifier: Modifier = Modifier,
    viewModel: VesselSelectorViewModel = hiltViewModel(),
    onAddVessel: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VesselSelectorBar(
        vessels = state.vessels,
        active = state.active,
        onSelect = viewModel::select,
        modifier = modifier,
        onAddVessel = onAddVessel,
    )
}

private val NAME_MAX_WIDTH = 180.dp
private val MENU_MIN_WIDTH = 200.dp
private val CHECK_SIZE = 20.dp

@Preview
@Composable
private fun VesselSelectorPreview() {
    val vessels = listOf(
        Vessel(id = "1", name = "MV Example", isActive = true, createdAt = 0, updatedAt = 0),
        Vessel(id = "2", name = "MT Karadeniz", createdAt = 0, updatedAt = 0),
    )
    DeckWatchTheme {
        VesselSelectorBar(vessels = vessels, active = vessels.first())
    }
}
