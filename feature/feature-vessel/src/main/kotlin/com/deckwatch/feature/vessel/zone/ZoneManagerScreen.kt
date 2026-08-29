package com.deckwatch.feature.vessel.zone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.ConfirmDialog
import com.deckwatch.feature.vessel.common.DeckPlanOutline
import com.deckwatch.feature.vessel.common.SwatchRow
import com.deckwatch.feature.vessel.common.Swatches
import com.deckwatch.feature.vessel.common.TeachingEmptyState
import com.deckwatch.feature.vessel.common.VesselTopBar
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets

/**
 * Spatial zones on one deck (§6.4), edited as rectangles — see [ZoneGeometry] for why the list
 * mode offers four sliders rather than polygon drawing.
 */
@Composable
fun ZoneManagerScreen(
    deckId: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ZoneManagerViewModel = hiltViewModel(),
) {
    LaunchedEffect(deckId) { viewModel.bind(deckId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Zone?>(null) }
    var addingNew by remember { mutableStateOf(false) }

    ZoneManagerContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAdd = { addingNew = true },
        onEdit = { editing = it },
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onDelete = viewModel::askDelete,
    )

    val zoneBeingEdited = editing
    if (addingNew || zoneBeingEdited != null) {
        ZoneEditDialog(
            zone = zoneBeingEdited,
            deck = state.deck,
            onSave = {
                viewModel.save(it)
                editing = null
                addingNew = false
            },
            onDismiss = {
                editing = null
                addingNew = false
            },
        )
    }

    val pending = deleteTarget
    if (pending != null) {
        val name = state.zones.firstOrNull { it.id == pending }?.name.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.zone_manager_delete_title),
            message = stringResource(R.string.zone_manager_delete_message, name),
            confirmLabel = stringResource(R.string.vessel_action_delete),
            onConfirm = { viewModel.confirmDelete(pending) },
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
internal fun ZoneManagerContent(
    state: ZoneManagerUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (Zone) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                VesselTopBar(
                    title = state.deck?.name ?: stringResource(R.string.zone_manager_title),
                    onBack = onBack,
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAdd, modifier = Modifier.size(Dimens.TouchTargetPrimary)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.zone_manager_add),
                    )
                }
            },
        ) { padding ->
            if (state.isEmpty) {
                TeachingEmptyState(
                    message = stringResource(R.string.zone_manager_empty_message),
                    actionLabel = stringResource(R.string.zone_manager_add),
                    onAction = onAdd,
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    itemsIndexed(items = state.zones, key = { _, zone -> zone.id }) { index, zone ->
                        ZoneRowItem(
                            zone = zone,
                            deck = state.deck,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.zones.lastIndex,
                            onEdit = { onEdit(zone) },
                            onMoveUp = { onMoveUp(zone.id) },
                            onMoveDown = { onMoveDown(zone.id) },
                            onDelete = { onDelete(zone.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneRowItem(
    zone: Zone,
    deck: Deck?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowComfortable)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        Box(
            modifier = Modifier
                .size(ZONE_DOT)
                .clip(CircleShape)
                .background(Color(zone.colorArgb)),
        )
        if (deck != null) {
            DeckPlanOutline(
                plan = deck.plan,
                fill = MaterialTheme.colorScheme.surfaceVariant,
                stroke = MaterialTheme.colorScheme.onSurfaceVariant,
                zone = zone.polygon,
                zoneColor = Color(zone.colorArgb).copy(alpha = ZONE_PREVIEW_ALPHA),
                modifier = Modifier.size(width = PLAN_THUMB_W, height = PLAN_THUMB_H),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.TouchTargetMin),
        ) {
            Text(
                text = zone.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "#${zone.sortOrder}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(Dimens.TouchTargetMin)) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.zone_manager_move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(Dimens.TouchTargetMin)) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.zone_manager_move_down),
            )
        }
        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
            Text(stringResource(R.string.vessel_action_edit))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTargetMin)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.vessel_action_delete),
            )
        }
    }
}

/** Name, colour and the four edge sliders, previewed live on the deck outline. */
@Composable
internal fun ZoneEditDialog(
    zone: Zone?,
    deck: Deck?,
    onSave: (ZoneDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(zone) { mutableStateOf(zone?.name.orEmpty()) }
    var colour by remember(zone) { mutableStateOf(zone?.colorArgb ?: Swatches.Default.argb) }
    var rect by remember(zone) {
        mutableStateOf(zone?.let { ZoneGeometry.polygonToRect(it.polygon) } ?: ZoneGeometry.Default)
    }
    var showNameError by remember(zone) { mutableStateOf(false) }
    val plan = deck?.plan ?: BuiltInPlanPresets.all.first().plan

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (zone == null) R.string.zone_edit_title_new else R.string.zone_edit_title_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = { Text(stringResource(R.string.zone_edit_name)) },
                    isError = showNameError,
                    singleLine = true,
                    supportingText = {
                        if (showNameError) {
                            Text(
                                text = stringResource(R.string.zone_edit_name_required),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.zone_edit_colour),
                    style = MaterialTheme.typography.labelLarge,
                )
                SwatchRow(selectedArgb = colour, onSelect = { colour = it })

                Text(
                    text = stringResource(R.string.zone_edit_rect_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val previewDescription = stringResource(R.string.zone_edit_cd_preview)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
                ) {
                    DeckPlanOutline(
                        plan = plan,
                        fill = MaterialTheme.colorScheme.surfaceVariant,
                        stroke = MaterialTheme.colorScheme.onSurfaceVariant,
                        zone = ZoneGeometry.rectToPolygon(rect),
                        zoneColor = Color(colour).copy(alpha = ZONE_PREVIEW_ALPHA),
                        modifier = Modifier
                            .size(width = PREVIEW_W, height = PREVIEW_H)
                            .clearAndSetSemantics { contentDescription = previewDescription },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        EdgeSlider(R.string.zone_edit_left, rect.left) { rect = rect.copy(left = it) }
                        EdgeSlider(R.string.zone_edit_top, rect.top) { rect = rect.copy(top = it) }
                        EdgeSlider(R.string.zone_edit_right, rect.right) { rect = rect.copy(right = it) }
                        EdgeSlider(R.string.zone_edit_bottom, rect.bottom) { rect = rect.copy(bottom = it) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSave(
                            ZoneDraft(
                                id = zone?.id,
                                name = name,
                                colorArgb = colour,
                                rect = ZoneGeometry.normalise(rect),
                            ),
                        )
                    }
                },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.vessel_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Text(stringResource(R.string.vessel_action_cancel))
            }
        },
    )
}

@Composable
private fun EdgeSlider(labelRes: Int, value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
        )
    }
}

private val ZONE_DOT = 16.dp
private val PLAN_THUMB_W = 28.dp
private val PLAN_THUMB_H = 40.dp
private val PREVIEW_W = 72.dp
private val PREVIEW_H = 104.dp
private const val ZONE_PREVIEW_ALPHA = 0.45f

@Preview
@Composable
private fun ZoneManagerPreview() {
    val deck = Deck(
        id = "d",
        vesselId = "v",
        name = "Upper Deck",
        shortCode = "UD",
        levelIndex = 0,
        plan = BuiltInPlanPresets.all.first().plan,
        createdAt = 0,
        updatedAt = 0,
    )
    DeckWatchTheme {
        ZoneManagerContent(
            state = ZoneManagerUiState(
                deck = deck,
                zones = listOf(
                    Zone(
                        id = "z1",
                        deckId = "d",
                        name = "Fwd Mooring Station",
                        polygon = ZoneGeometry.rectToPolygon(ZoneGeometry.Default),
                        colorArgb = Swatches.All[2].argb,
                        sortOrder = 0,
                    ),
                ),
                isLoading = false,
            ),
        )
    }
}
