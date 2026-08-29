package com.deckwatch.feature.vessel.manager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.Vessel
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.ConfirmDialog
import com.deckwatch.feature.vessel.common.ImoStatus
import com.deckwatch.feature.vessel.common.TeachingEmptyState
import com.deckwatch.feature.vessel.common.VesselTopBar
import com.deckwatch.feature.vessel.common.label
import com.deckwatch.feature.vessel.edit.VesselEditScreen

/**
 * The vessel manager of §5 — an officer changes ship, so DeckWatch keeps several and marks one
 * active.
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
            message = stringResource(R.string.vessel_manager_delete_message, name),
            confirmLabel = stringResource(R.string.vessel_action_delete),
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
            VesselTopBar(
                title = stringResource(R.string.vessel_manager_title),
                onBack = onBack,
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
            TeachingEmptyState(
                message = stringResource(R.string.vessel_manager_empty_message),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowComfortable)
            .clickable(onClick = onOpen)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        if (row.isActive) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.vessel_manager_cd_active),
                tint = ConditionColors.Good,
                modifier = Modifier.size(Dimens.SpacingXl),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.vessel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                Text(
                    text = row.vessel.imoNumber?.let { "IMO $it" }
                        ?: stringResource(R.string.vessel_manager_no_imo),
                    style = tagTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.vessel.flag.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.imoStatus.needsWarning) {
                UnverifiedImoBadge()
            }
        }
        if (row.isActive) {
            Text(
                text = stringResource(R.string.vessel_manager_active),
                style = MaterialTheme.typography.labelMedium,
                color = ConditionColors.Good,
            )
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

/** Shown wherever an IMO number that fails its check digit is displayed — see [ImoStatus]. */
@Composable
internal fun UnverifiedImoBadge(modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(text = stringResource(R.string.vessel_manager_imo_unverified)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = ConditionColors.Monitor,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

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
