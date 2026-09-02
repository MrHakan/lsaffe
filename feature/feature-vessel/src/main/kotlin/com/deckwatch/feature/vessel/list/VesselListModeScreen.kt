package com.deckwatch.feature.vessel.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DueDeltaChip
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.ConditionIndicator
import com.deckwatch.feature.vessel.common.DeckPlanThumbnail
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
import com.deckwatch.feature.vessel.deck.presetName

/**
 * LIST MODE — §7.1C. A grouped list of Deck → Zone → Equipment for the active vessel, with no
 * graphics on the critical path. Every function the deck canvas will offer has to be reachable
 * from here too.
 *
 * The screen renders *inside* the Vessel tab's chrome, so it draws no top bar of its own
 * (DESIGN_OVERHAUL rule 2) and no add-equipment action — the tab owns that FAB. Its only primary
 * action is the first-run "Add your first deck", with the six plan presets visible immediately.
 */
@Composable
fun VesselListModeScreen(
    onOpenEquipment: (String) -> Unit = {},
    onAddDeck: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VesselListModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val context = LocalContext.current

    VesselListModeContent(
        state = state,
        presets = presets,
        modifier = modifier,
        onOpenEquipment = onOpenEquipment,
        onAddDeck = onAddDeck,
        onPickPreset = { preset ->
            viewModel.createDeckFromPreset(preset, presetDisplayName(context, preset))
        },
    )
}

@Composable
internal fun VesselListModeContent(
    state: ListModeUiState,
    presets: List<PlanPreset> = BuiltInPlanPresets.all,
    modifier: Modifier = Modifier,
    onOpenEquipment: (String) -> Unit = {},
    onAddDeck: () -> Unit = {},
    onPickPreset: (PlanPreset) -> Unit = {},
) {
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    val today = remember { Dates.todayEpochDay() }

    when {
        !state.hasVessel && !state.isLoading -> EmptyState(
            icon = Icons.Filled.DirectionsBoat,
            title = stringResource(R.string.list_mode_no_vessel_title),
            body = stringResource(R.string.list_mode_no_vessel),
            modifier = modifier,
        )

        state.hasNoDecks -> EmptyState(
            icon = Icons.Filled.Layers,
            title = stringResource(R.string.list_mode_empty_title),
            body = stringResource(R.string.list_mode_empty_message),
            actionLabel = stringResource(R.string.deck_manager_add_first),
            onAction = onAddDeck,
            modifier = modifier,
            extraContent = {
                PresetStrip(
                    presets = presets,
                    onPick = onPickPreset,
                    modifier = Modifier.padding(top = Dimens.SpacingXl),
                )
            },
        )

        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            for (group in state.groups) {
                val isCollapsed = group.key in collapsed
                item(key = "deck-${group.key}") {
                    DeckHeader(
                        group = group,
                        collapsed = isCollapsed,
                        onToggle = {
                            collapsed = if (isCollapsed) collapsed - group.key else collapsed + group.key
                        },
                    )
                    HorizontalDivider()
                }
                if (isCollapsed) continue
                if (group.equipmentCount == 0) {
                    item(key = "empty-${group.key}") { DeckEmptyLine() }
                }
                for (zoneGroup in group.zoneGroups) {
                    item(key = "zone-${group.key}-${zoneGroup.key}") {
                        SectionHeader(
                            text = zoneGroup.zone?.name ?: stringResource(R.string.list_mode_no_zone),
                        )
                    }
                    items(
                        items = zoneGroup.equipment,
                        key = { "eq-${it.id}" },
                    ) { equipment ->
                        EquipmentListRow(
                            equipment = equipment,
                            type = state.types[equipment.typeKey],
                            today = today,
                            onClick = { onOpenEquipment(equipment.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** The six presets, visible immediately in the first-run empty state — §14. */
@Composable
private fun PresetStrip(
    presets: List<PlanPreset>,
    onPick: (PlanPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        items(items = presets, key = { it.key }) { preset ->
            Column(
                modifier = Modifier
                    .widthIn(min = PRESET_TILE_WIDTH, max = PRESET_TILE_WIDTH)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(Dimens.CardCorner),
                    )
                    .clickable(role = Role.Button) { onPick(preset) }
                    .padding(Dimens.SpacingS),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                DeckPlanThumbnail(
                    plan = preset.plan,
                    fill = MaterialTheme.colorScheme.surfaceVariant,
                    stroke = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = presetName(preset),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeckHeader(
    group: DeckGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val deck = group.deck
    DeckWatchListRow(
        title = deck?.name ?: stringResource(R.string.list_mode_unplaced_deck),
        subtitle = deck?.let { deckSubtitle(it) },
        onClick = onToggle,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                Text(
                    text = group.equipmentCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    imageVector = if (collapsed) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.Filled.KeyboardArrowUp
                    },
                    contentDescription = stringResource(
                        if (collapsed) R.string.vessel_cd_expand else R.string.vessel_cd_collapse,
                    ),
                )
            }
        },
    )
}

/** "UD · Level 0" — the spine code and where the deck sits in the stack. */
@Composable
private fun deckSubtitle(deck: Deck): String {
    val code = deck.shortCode?.takeIf { it.isNotBlank() }
    val level = stringResource(R.string.deck_manager_level, deck.levelIndex)
    return listOfNotNull(code, level).joinToString(SEPARATOR)
}

@Composable
private fun DeckEmptyLine() {
    Text(
        text = stringResource(R.string.list_mode_deck_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingXl, vertical = Dimens.SpacingM),
    )
}

@Composable
private fun EquipmentListRow(
    equipment: Equipment,
    type: EquipmentType?,
    today: Long,
    onClick: () -> Unit,
) {
    val language = LocalContext.current.resources.configuration.locales[0].language
    val typeName = type?.let { if (language == TURKISH_LANGUAGE) it.nameTr else it.nameEn }
    val due = equipment.nextDueDate
    DeckWatchListRow(
        title = equipment.tag,
        titleIsTag = true,
        subtitle = equipment.name ?: typeName ?: equipment.typeKey,
        onClick = onClick,
        modifier = Modifier.padding(start = Dimens.SpacingM),
        leading = { SymbolTile(symbolKey = equipment.symbolKey, size = SYMBOL_SIZE) },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                if (due != null) {
                    // Semantic colour for how late it is, and the date itself in the label — the
                    // colour is never the only signal (rule 6).
                    DueDeltaChip(
                        daysUntilDue = due - today,
                        text = stringResource(R.string.list_mode_due, Dates.formatIso(due)),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.list_mode_no_due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConditionIndicator(grade = equipment.condition)
            }
        },
    )
}

/** Preset name outside composition, for the view model call that creates the deck. */
private fun presetDisplayName(context: android.content.Context, preset: PlanPreset): String {
    val language = context.resources.configuration.locales[0].language
    return if (language == TURKISH_LANGUAGE) preset.nameTr else preset.nameEn
}

private val PRESET_TILE_WIDTH = 96.dp
private val SYMBOL_SIZE = 36.dp
private const val TURKISH_LANGUAGE = "tr"
private const val SEPARATOR = " · "

@Preview
@Composable
private fun VesselListModePreview() {
    val deck = Deck(
        id = "d1",
        vesselId = "v",
        name = "Upper Deck",
        shortCode = "UD",
        levelIndex = 0,
        plan = BuiltInPlanPresets.all.first().plan,
        createdAt = 0,
        updatedAt = 0,
    )
    DeckWatchTheme {
        Box {
            VesselListModeContent(
                state = ListModeUiState(
                    vessel = Vessel(id = "v", name = "MV Example", createdAt = 0, updatedAt = 0),
                    groups = listOf(
                        DeckGroup(
                            deck = deck,
                            zoneGroups = listOf(
                                ZoneGroup(
                                    zone = null,
                                    equipment = listOf(
                                        Equipment(
                                            id = "e1",
                                            vesselId = "v",
                                            deckId = "d1",
                                            typeKey = "FFE_PORTABLE_EXTINGUISHER",
                                            symbolKey = "FES001",
                                            tag = "FE-UD-01",
                                            condition = ConditionGrade.MONITOR,
                                            createdAt = 0,
                                            updatedAt = 0,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    isLoading = false,
                ),
            )
        }
    }
}
