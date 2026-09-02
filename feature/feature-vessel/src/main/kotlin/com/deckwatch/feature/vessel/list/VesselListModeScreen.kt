package com.deckwatch.feature.vessel.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.deckwatch.core.designsystem.components.PlateHeading
import com.deckwatch.core.designsystem.components.StatusSpine
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.components.TagPlate
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.LocalListDensity
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.DeckPlanThumbnail
import com.deckwatch.feature.vessel.common.PrimaryButton
import com.deckwatch.feature.vessel.common.TeachingEmptyState
import com.deckwatch.feature.vessel.common.VesselTopBar
import com.deckwatch.feature.vessel.common.labelRes
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
import com.deckwatch.feature.vessel.deck.presetName

/**
 * LIST MODE — §7.1C. A grouped list of Deck → Zone → Equipment for the active vessel, with no
 * graphics on the critical path. Every function the deck canvas will offer has to be reachable
 * from here too.
 */
@Composable
fun VesselListModeScreen(
    onOpenEquipment: (String) -> Unit = {},
    onAddDeck: () -> Unit = {},
    modifier: Modifier = Modifier,
    onAddVessel: (() -> Unit)? = null,
    onAddEquipment: ((deckId: String?, zoneId: String?) -> Unit)? = null,
    topBarActions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
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
        onAddVessel = onAddVessel,
        onAddEquipment = onAddEquipment,
        topBarActions = topBarActions,
        floatingActionButton = floatingActionButton,
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
    onAddVessel: (() -> Unit)? = null,
    onAddEquipment: ((deckId: String?, zoneId: String?) -> Unit)? = null,
    topBarActions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    onPickPreset: (PlanPreset) -> Unit = {},
) {
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VesselTopBar(
                title = state.vessel?.name ?: stringResource(R.string.list_mode_title),
                actions = topBarActions,
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        when {
            !state.hasVessel && !state.isLoading -> TeachingEmptyState(
                title = stringResource(R.string.list_mode_no_vessel_title),
                message = stringResource(R.string.list_mode_no_vessel),
                actionLabel = onAddVessel?.let { stringResource(R.string.list_mode_add_vessel) },
                onAction = onAddVessel,
                modifier = Modifier.padding(padding),
            )

            state.hasNoDecks -> TeachingEmptyState(
                title = stringResource(R.string.list_mode_empty_title),
                message = stringResource(R.string.list_mode_empty_message),
                actionLabel = stringResource(R.string.deck_manager_add_first),
                onAction = onAddDeck,
                modifier = Modifier.padding(padding),
                content = { PresetStrip(presets = presets, onPick = onPickPreset) },
            )

            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                for (group in state.groups) {
                    val isCollapsed = group.key in collapsed
                    item(key = "deck-${group.key}") {
                        DeckHeader(
                            group = group,
                            collapsed = isCollapsed,
                            onToggle = {
                                collapsed = if (isCollapsed) collapsed - group.key else collapsed + group.key
                            },
                            onAddEquipment = onAddEquipment?.takeIf { !group.isUnplaced }?.let { add ->
                                { add(group.deck?.id, null) }
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
                            ZoneHeader(
                                zoneGroup = zoneGroup,
                                onAddEquipment = onAddEquipment
                                    ?.takeIf { zoneGroup.zone != null }
                                    ?.let { add -> { add(group.deck?.id, zoneGroup.zone?.id) } },
                            )
                        }
                        items(
                            items = zoneGroup.equipment,
                            key = { "eq-${it.id}" },
                        ) { equipment ->
                            EquipmentListRow(
                                equipment = equipment,
                                type = state.types[equipment.typeKey],
                                onClick = { onOpenEquipment(equipment.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                item(key = "add-deck") {
                    PrimaryButton(
                        text = stringResource(R.string.deck_manager_add_above),
                        onClick = onAddDeck,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.SpacingL),
                    )
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
    onAddEquipment: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowCompact)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        val deck = group.deck
        // The deck code is an identifier stencilled on the ship, so it is set like every other
        // identifier in the app rather than as loose monospace text.
        if (deck?.shortCode != null) {
            TagPlate(tag = deck.shortCode.orEmpty())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deck?.name ?: stringResource(R.string.list_mode_unplaced_deck),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (deck != null) {
                Text(
                    text = stringResource(R.string.deck_manager_level, deck.levelIndex),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(text = group.equipmentCount.toString(), style = MaterialTheme.typography.labelLarge)
        if (onAddEquipment != null) {
            IconButton(onClick = onAddEquipment, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(
                        R.string.list_mode_add_equipment_here,
                        group.deck?.name.orEmpty(),
                    ),
                )
            }
        }
        Icon(
            imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(
                if (collapsed) R.string.vessel_cd_expand else R.string.vessel_cd_collapse,
            ),
        )
    }
}

@Composable
private fun ZoneHeader(zoneGroup: ZoneGroup, onAddEquipment: (() -> Unit)? = null) {
    // A zone is a subdivision of the deck above it, so it is set as a rule with a tracked label
    // rather than as coloured text: colour in this app means condition, and nothing else.
    PlateHeading(
        text = zoneGroup.zone?.name ?: stringResource(R.string.list_mode_no_zone),
        modifier = Modifier.padding(
            start = Dimens.SpacingXl,
            end = Dimens.SpacingL,
            top = Dimens.SpacingM,
            bottom = Dimens.SpacingXs,
        ),
        trailing = {
            if (onAddEquipment != null) {
                IconButton(onClick = onAddEquipment, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(
                            R.string.list_mode_add_equipment_here,
                            zoneGroup.zone?.name.orEmpty(),
                        ),
                    )
                }
            }
        },
    )
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
    onClick: () -> Unit,
) {
    val language = LocalContext.current.resources.configuration.locales[0].language
    val typeName = type?.let { if (language == TURKISH_LANGUAGE) it.nameTr else it.nameEn }
    val rowHeight = Dimens.rowHeight(LocalListDensity.current)
    val due = dueSummary(equipment.nextDueDate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(onClick = onClick)
            .padding(start = Dimens.SpacingL, end = Dimens.SpacingL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        // Condition leads the row rather than trailing it: it is the thing being scanned for, and
        // at the leading edge it survives sunlight, gloves and the hand holding the phone.
        StatusSpine(
            color = ConditionColors.of(equipment.condition),
            contentDescription = stringResource(
                R.string.deck_manager_condition,
                stringResource(equipment.condition.labelRes),
            ),
            modifier = Modifier.height(rowHeight - Dimens.SpacingM),
        )
        SymbolTile(symbolKey = equipment.symbolKey, size = SYMBOL_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = equipment.name ?: typeName ?: equipment.typeKey,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TagPlate(tag = equipment.tag, modifier = Modifier.padding(top = Dimens.SpacingXs))
        }
        Text(
            text = due.text,
            style = MaterialTheme.typography.labelMedium,
            color = due.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/** What the due column says, and whether it says it in a colour. */
private data class DueSummary(val text: String, val color: Color?)

/**
 * The due column, as a delta rather than a date.
 *
 * "2026-09-14" makes the reader do arithmetic against today; "12 d overdue" is the answer they
 * were going to work out. The date itself is one tap away on the item's own sheet, where there is
 * room for it and where it is being read rather than scanned.
 *
 * Colour appears only when the answer is *act now* — overdue, or due today. Anything further out
 * is neutral, so that a screen of colour never dilutes the rows that need it.
 */
@Composable
private fun dueSummary(nextDueDate: Long?): DueSummary {
    if (nextDueDate == null) {
        return DueSummary(stringResource(R.string.list_mode_no_due), null)
    }
    val days = nextDueDate - Dates.todayEpochDay()
    return when {
        days < 0 -> DueSummary(
            stringResource(R.string.list_mode_due_overdue, -days),
            ConditionColors.OutOfService,
        )
        days == 0L -> DueSummary(stringResource(R.string.list_mode_due_today), ConditionColors.Monitor)
        days <= DUE_SOON_DAYS -> DueSummary(stringResource(R.string.list_mode_due_in_days, days), null)
        else -> DueSummary(stringResource(R.string.list_mode_due, Dates.formatIso(nextDueDate)), null)
    }
}

/** Inside this many days the delta is more useful than the date; beyond it, the date is. */
private const val DUE_SOON_DAYS = 30L

/** Preset name outside composition, for the view model call that creates the deck. */
private fun presetDisplayName(context: android.content.Context, preset: PlanPreset): String {
    val language = context.resources.configuration.locales[0].language
    return if (language == TURKISH_LANGUAGE) preset.nameTr else preset.nameEn
}

private val PRESET_TILE_WIDTH = 96.dp
private val SYMBOL_SIZE = 36.dp
private const val TURKISH_LANGUAGE = "tr"

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
