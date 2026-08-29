package com.deckwatch.feature.vessel.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.ConditionDot
import com.deckwatch.feature.vessel.common.ConfirmDialog
import com.deckwatch.feature.vessel.common.DeckPlanOutline
import com.deckwatch.feature.vessel.common.TeachingEmptyState
import com.deckwatch.feature.vessel.common.VesselTopBar
import com.deckwatch.feature.vessel.zone.ZoneManagerScreen

/**
 * The deck stack as a list, sorted by `levelIndex` descending (§6.2, §7.1C).
 *
 * A null [vesselId] resolves to the active vessel. Zones open through [onOpenZones] when a host
 * graph supplies one; otherwise the screen opens the zone manager itself, so deck → zone works
 * without navigation wiring.
 */
@Composable
fun DeckManagerScreen(
    vesselId: String? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenZones: ((String) -> Unit)? = null,
    viewModel: DeckManagerViewModel = hiltViewModel(),
) {
    LaunchedEffect(vesselId) { viewModel.bind(vesselId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var inlineZonesFor by remember { mutableStateOf<String?>(null) }

    DeckManagerContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAddAbove = viewModel::openAddAbove,
        onAddBelow = viewModel::openAddBelow,
        onInsertBetween = viewModel::openInsertBetween,
        onEdit = viewModel::openEdit,
        onDelete = viewModel::askDeleteDeck,
        onOpenZones = { id -> onOpenZones?.invoke(id) ?: run { inlineZonesFor = id } },
    )

    when (val target = sheet) {
        null -> Unit
        is DeckSheetTarget.Edit -> DeckEditSheet(
            presets = presets,
            initial = target.deck,
            onSave = viewModel::saveDraft,
            onDismiss = viewModel::closeSheet,
        )

        else -> DeckEditSheet(
            presets = presets,
            initial = null,
            onSave = viewModel::saveDraft,
            onDismiss = viewModel::closeSheet,
        )
    }

    val pending = deleteTarget
    if (pending != null) {
        val row = state.decks.firstOrNull { it.id == pending }
        ConfirmDialog(
            title = stringResource(R.string.deck_manager_delete_title, row?.deck?.name.orEmpty()),
            message = if (row == null || row.equipmentCount == 0) {
                stringResource(R.string.deck_manager_delete_message_empty)
            } else {
                stringResource(R.string.deck_manager_delete_message_equipment, row.equipmentCount)
            },
            confirmLabel = stringResource(R.string.vessel_action_delete),
            onConfirm = { viewModel.confirmDeleteDeck(pending) },
            onDismiss = viewModel::cancelDeleteDeck,
        )
    }

    val zonesFor = inlineZonesFor
    if (zonesFor != null) {
        Dialog(
            onDismissRequest = { inlineZonesFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ZoneManagerScreen(deckId = zonesFor, onBack = { inlineZonesFor = null })
        }
    }
}

@Composable
internal fun DeckManagerContent(
    state: DeckManagerUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAddAbove: () -> Unit = {},
    onAddBelow: () -> Unit = {},
    onInsertBetween: (InsertSlot) -> Unit = {},
    onEdit: (Deck) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onOpenZones: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VesselTopBar(
                title = state.vessel?.name ?: stringResource(R.string.deck_manager_title),
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (state.hasVessel && state.decks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddAbove,
                    modifier = Modifier.size(Dimens.TouchTargetPrimary),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.deck_manager_add_above),
                    )
                }
            }
        },
    ) { padding ->
        when {
            !state.hasVessel && !state.isLoading -> TeachingEmptyState(
                message = stringResource(R.string.deck_manager_no_vessel),
                modifier = Modifier.padding(padding),
            )

            state.isEmpty -> TeachingEmptyState(
                message = stringResource(R.string.deck_manager_empty_message),
                actionLabel = stringResource(R.string.deck_manager_add_first),
                onAction = onAddAbove,
                modifier = Modifier.padding(padding),
            )

            else -> DeckList(
                state = state,
                onAddAbove = onAddAbove,
                onAddBelow = onAddBelow,
                onInsertBetween = onInsertBetween,
                onEdit = onEdit,
                onDelete = onDelete,
                onOpenZones = onOpenZones,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DeckList(
    state: DeckManagerUiState,
    onAddAbove: () -> Unit,
    onAddBelow: () -> Unit,
    onInsertBetween: (InsertSlot) -> Unit,
    onEdit: (Deck) -> Unit,
    onDelete: (String) -> Unit,
    onOpenZones: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "add-above") {
            StackEndAction(
                label = stringResource(R.string.deck_manager_add_above),
                icon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                onClick = onAddAbove,
            )
        }
        state.decks.forEachIndexed { index, row ->
            item(key = row.id) {
                DeckRowItem(
                    row = row,
                    onEdit = { onEdit(row.deck) },
                    onDelete = { onDelete(row.id) },
                    onOpenZones = { onOpenZones(row.id) },
                    onInsertAbove = {
                        val slot = state.insertSlots.getOrNull(index - 1)
                        if (slot == null) onAddAbove() else onInsertBetween(slot)
                    },
                    onInsertBelow = {
                        val slot = state.insertSlots.getOrNull(index)
                        if (slot == null) onAddBelow() else onInsertBetween(slot)
                    },
                )
                HorizontalDivider()
            }
            val slot = state.insertSlots.getOrNull(index)
            if (slot != null) {
                item(key = "slot-${slot.upperLevelIndex}-${slot.lowerLevelIndex}") {
                    InsertBetweenRow(slot = slot, onClick = { onInsertBetween(slot) })
                    HorizontalDivider()
                }
            }
        }
        item(key = "add-below") {
            StackEndAction(
                label = stringResource(R.string.deck_manager_add_below),
                icon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                onClick = onAddBelow,
            )
        }
    }
}

@Composable
private fun StackEndAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary),
    ) {
        icon()
        Text(text = label, modifier = Modifier.padding(start = Dimens.SpacingS))
    }
}

@Composable
private fun InsertBetweenRow(slot: InsertSlot, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = slot.enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(
            text = if (slot.enabled) {
                stringResource(R.string.deck_manager_insert_between)
            } else {
                stringResource(R.string.deck_manager_insert_between_full)
            },
            modifier = Modifier.padding(start = Dimens.SpacingS),
        )
    }
}

@Composable
private fun DeckRowItem(
    row: DeckRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenZones: () -> Unit,
    onInsertAbove: () -> Unit,
    onInsertBelow: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowComfortable)
            .clickable(onClick = onOpenZones)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        ShortCodePill(code = row.deck.shortCode, tintArgb = row.deck.colorTint)
        DeckPlanOutline(
            plan = row.deck.plan,
            fill = MaterialTheme.colorScheme.surfaceVariant,
            stroke = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = PLAN_THUMB_W, height = PLAN_THUMB_H),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.deck.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                Text(
                    text = stringResource(R.string.deck_manager_level, row.levelIndex),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (row.equipmentCount == 0) {
                        stringResource(R.string.deck_manager_no_equipment)
                    } else {
                        pluralStringResource(
                            R.plurals.deck_manager_equipment_count,
                            row.equipmentCount,
                            row.equipmentCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ConditionDot(grade = row.worstCondition)
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.vessel_cd_more_actions),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DeckMenuItem(R.string.deck_manager_insert_above) {
                    menuOpen = false
                    onInsertAbove()
                }
                DeckMenuItem(R.string.deck_manager_insert_below) {
                    menuOpen = false
                    onInsertBelow()
                }
                DeckMenuItem(R.string.zone_manager_title) {
                    menuOpen = false
                    onOpenZones()
                }
                DeckMenuItem(R.string.vessel_action_edit) {
                    menuOpen = false
                    onEdit()
                }
                DeckMenuItem(R.string.vessel_action_delete) {
                    menuOpen = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun DeckMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
    )
}

/** The stack-spine pill: monospace short code on the deck's own tint (§7.2 deck spine). */
@Composable
private fun ShortCodePill(code: String?, tintArgb: Int?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(PILL_WIDTH)
            .heightIn(min = Dimens.TouchTargetMin)
            .clip(RoundedCornerShape(Dimens.ChipCorner))
            .background(deckTintColor(tintArgb)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code.orEmpty(),
            style = tagTextStyle(),
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 1,
        )
    }
}

private val PILL_WIDTH = 48.dp
private val PLAN_THUMB_W = 28.dp
private val PLAN_THUMB_H = 40.dp

@Preview
@Composable
private fun DeckManagerPreview() {
    val vessel = Vessel(id = "v", name = "MV Example", createdAt = 0, updatedAt = 0)
    val plan = BuiltInPlanPresets.all.first().plan
    DeckWatchTheme {
        DeckManagerContent(
            state = DeckManagerUiState(
                vessel = vessel,
                decks = listOf(
                    DeckRow(
                        Deck("1", "v", "Bridge Deck", "BR", 20, plan, createdAt = 0, updatedAt = 0),
                        equipmentCount = 3,
                        worstCondition = ConditionGrade.GOOD,
                    ),
                    DeckRow(
                        Deck("2", "v", "Upper Deck", "UD", 0, plan, createdAt = 0, updatedAt = 0),
                        equipmentCount = 14,
                        worstCondition = ConditionGrade.DEFECTIVE,
                    ),
                ),
                insertSlots = listOf(InsertSlot(0, 20, enabled = true)),
                isLoading = false,
            ),
        )
    }
}
