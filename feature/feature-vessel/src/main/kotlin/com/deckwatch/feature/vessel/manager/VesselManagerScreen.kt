package com.deckwatch.feature.vessel.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBoat
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.ImoStatus
import com.deckwatch.feature.vessel.common.label
import com.deckwatch.feature.vessel.edit.VesselEditScreen

/**
 * The vessel manager of §5 — an officer changes ship, so DeckWatch keeps several and marks one
 * active.
 *
 * One primary action (DESIGN_OVERHAUL rule 1): the "Add vessel" FAB. Set active / edit / delete
 * are per-row overflow items, so nothing competes with it.
 *
 * [onAddVessel] and [onEditVessel] let a host graph route to its own destination. Left null, the
 * manager opens [VesselEditScreen] itself in a full-screen dialog, so the screen is complete on
 * its own rather than depending on navigation that has not been wired yet.
 */
@Composable
fun VesselManagerScreen(
    onBack: () -> Unit = {},
    onOpenVessel: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onAddVessel: (() -> Unit)? = null,
    onEditVessel: ((String) -> Unit)? = null,
    viewModel: VesselManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    var inlineEdit by remember { mutableStateOf<InlineEditTarget?>(null) }

    VesselManagerContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onOpenVessel = onOpenVessel,
        onSetActive = viewModel::setActive,
        onDelete = viewModel::askDelete,
        onAdd = { onAddVessel?.invoke() ?: run { inlineEdit = InlineEditTarget(null) } },
        onEdit = { id -> onEditVessel?.invoke(id) ?: run { inlineEdit = InlineEditTarget(id) } },
    )

    val target = deleteTarget
    if (target != null) {
        val name = state.vessels.firstOrNull { it.id == target }?.vessel?.name.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.vessel_manager_delete_title),
            body = stringResource(R.string.vessel_manager_delete_message, name),
            confirmLabel = stringResource(R.string.vessel_action_delete),
            cancelLabel = stringResource(R.string.vessel_action_cancel),
            onConfirm = { viewModel.confirmDelete(target) },
            onDismiss = viewModel::cancelDelete,
        )
    }

    val editing = inlineEdit
    if (editing != null) {
        Dialog(
            onDismissRequest = { inlineEdit = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            VesselEditScreen(
                vesselId = editing.vesselId,
                onDone = { inlineEdit = null },
            )
        }
    }
}

private data class InlineEditTarget(val vesselId: String?)

@Composable
internal fun VesselManagerContent(
    state: VesselManagerUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenVessel: (String) -> Unit = {},
    onSetActive: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DeckWatchTopBar(
                title = stringResource(R.string.vessel_manager_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.vessel_cd_back),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.size(Dimens.TouchTargetPrimary),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.vessel_manager_add),
                )
            }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.DirectionsBoat,
                title = stringResource(R.string.vessel_manager_empty_title),
                body = stringResource(R.string.vessel_manager_empty_message),
                actionLabel = stringResource(R.string.vessel_manager_add),
                onAction = onAdd,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(items = state.vessels, key = { it.id }) { row ->
                    VesselRowItem(
                        row = row,
                        onOpen = { onOpenVessel(row.id) },
                        onSetActive = { onSetActive(row.id) },
                        onEdit = { onEdit(row.id) },
                        onDelete = { onDelete(row.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun VesselRowItem(
    row: VesselRow,
    onOpen: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val activeDescription = stringResource(R.string.vessel_manager_cd_active)
    DeckWatchListRow(
        title = row.vessel.name,
        subtitle = vesselSubtitle(row),
        onClick = onOpen,
        leading = {
            if (row.isActive) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = activeDescription,
                    tint = ConditionColors.Good,
                    modifier = Modifier.size(LEADING_ICON),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.DirectionsBoat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(LEADING_ICON),
                )
            }
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                if (row.imoStatus.needsWarning) {
                    UnverifiedImoBadge()
                }
                RowOverflowMenu(
                    menuOpen = menuOpen,
                    onOpenMenu = { menuOpen = true },
                    onDismissMenu = { menuOpen = false },
                    isActive = row.isActive,
                    onSetActive = onSetActive,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        },
    )
}

/**
 * "Active · IMO 9074729 · Marshall Islands". The active state is spelled out rather than left to
 * the green tick — colour is never the only signal (DESIGN_OVERHAUL rule 6).
 */
@Composable
private fun vesselSubtitle(row: VesselRow): String {
    val active = stringResource(R.string.vessel_manager_active).takeIf { row.isActive }
    val imo = row.vessel.imoNumber?.let { stringResource(R.string.vessel_manager_imo, it) }
        ?: stringResource(R.string.vessel_manager_no_imo)
    return listOfNotNull(active, imo, row.vessel.flag.label()).joinToString(SEPARATOR)
}

/** The per-row overflow: set active, edit, delete. */
@Composable
private fun RowOverflowMenu(
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        IconButton(onClick = onOpenMenu, modifier = Modifier.size(Dimens.TouchTargetMin)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.vessel_cd_more_actions),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            if (!isActive) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vessel_manager_set_active)) },
                    onClick = {
                        onDismissMenu()
                        onSetActive()
                    },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vessel_action_edit)) },
                onClick = {
                    onDismissMenu()
                    onEdit()
                },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vessel_action_delete)) },
                onClick = {
                    onDismissMenu()
                    onDelete()
                },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
        }
    }
}

/** Shown wherever an IMO number that fails its check digit is displayed — see [ImoStatus]. */
@Composable
internal fun UnverifiedImoBadge(modifier: Modifier = Modifier) {
    StatusChip(
        text = stringResource(R.string.vessel_manager_imo_unverified),
        color = ConditionColors.Monitor,
        modifier = modifier,
    )
}

private val LEADING_ICON = 24.dp
private const val SEPARATOR = " · "

@Preview
@Composable
private fun VesselManagerPreview() {
    val now = 0L
    DeckWatchTheme {
        VesselManagerContent(
            state = VesselManagerUiState(
                vessels = listOf(
                    VesselRow(
                        vessel = Vessel(
                            id = "1",
                            name = "MV Example",
                            imoNumber = "9074729",
                            isActive = true,
                            createdAt = now,
                            updatedAt = now,
                        ),
                        imoStatus = ImoStatus.VALID,
                    ),
                    VesselRow(
                        vessel = Vessel(
                            id = "2",
                            name = "MT Karadeniz",
                            imoNumber = "9074720",
                            createdAt = now,
                            updatedAt = now,
                        ),
                        imoStatus = ImoStatus.INVALID,
                    ),
                ),
                isLoading = false,
            ),
        )
    }
}
