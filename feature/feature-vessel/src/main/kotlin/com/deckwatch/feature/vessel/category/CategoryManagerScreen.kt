package com.deckwatch.feature.vessel.category

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Category
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.SwatchRow
import com.deckwatch.feature.vessel.common.Swatches
import com.deckwatch.feature.vessel.common.requiredLabel

/**
 * CRUD over the logical categories of §6.4. A null [vesselId] resolves to the active vessel; a
 * category can be scoped to that vessel or made global.
 *
 * One primary action (DESIGN_OVERHAUL rule 1): the "Add category" FAB.
 */
@Composable
fun CategoryManagerScreen(
    vesselId: String? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CategoryManagerViewModel = hiltViewModel(),
) {
    LaunchedEffect(vesselId) { viewModel.bind(vesselId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var addingNew by remember { mutableStateOf(false) }

    CategoryManagerContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onAdd = { addingNew = true },
        onEdit = { editing = it },
        onDelete = viewModel::askDelete,
    )

    val category = editing
    if (addingNew || category != null) {
        CategoryEditDialog(
            category = category,
            canScopeToVessel = state.vessel != null,
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
        val name = state.categories.firstOrNull { it.id == pending }?.name.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.category_manager_delete_title),
            body = stringResource(R.string.category_manager_delete_message, name),
            confirmLabel = stringResource(R.string.vessel_action_delete),
            cancelLabel = stringResource(R.string.vessel_action_cancel),
            onConfirm = { viewModel.confirmDelete(pending) },
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
internal fun CategoryManagerContent(
    state: CategoryManagerUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (Category) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                DeckWatchTopBar(
                    title = stringResource(R.string.category_manager_title),
                    subtitle = state.vessel?.name,
                    onBack = onBack,
                    backContentDescription = stringResource(R.string.vessel_cd_back),
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAdd, modifier = Modifier.size(Dimens.TouchTargetPrimary)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.category_manager_add),
                    )
                }
            },
        ) { padding ->
            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = stringResource(R.string.category_manager_empty_title),
                    body = stringResource(R.string.category_manager_empty_message),
                    actionLabel = stringResource(R.string.category_manager_add),
                    onAction = onAdd,
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    items(items = state.categories, key = { it.id }) { category ->
                        CategoryRowItem(
                            category = category,
                            onEdit = { onEdit(category) },
                            onDelete = { onDelete(category.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRowItem(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    DeckWatchListRow(
        title = category.name,
        subtitle = categorySubtitle(category),
        onClick = onEdit,
        leading = {
            Box(
                modifier = Modifier
                    .size(CATEGORY_DOT)
                    .clip(CircleShape)
                    .background(Color(category.colorArgb)),
            )
        },
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.vessel_cd_more_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vessel_action_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vessel_action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    )
                }
            }
        },
    )
}

/** "Global · Amber" — the scope in words, and the swatch named so colour is not the only signal. */
@Composable
private fun categorySubtitle(category: Category): String {
    val scope = stringResource(
        if (category.vesselId == null) R.string.category_scope_global else R.string.category_scope_vessel,
    )
    return scope + SEPARATOR + stringResource(Swatches.of(category.colorArgb).labelRes)
}

@Composable
internal fun CategoryEditDialog(
    category: Category?,
    canScopeToVessel: Boolean,
    onSave: (CategoryDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(category) { mutableStateOf(category?.name.orEmpty()) }
    var colour by remember(category) { mutableStateOf(category?.colorArgb ?: Swatches.Default.argb) }
    var isGlobal by remember(category) {
        mutableStateOf(category?.vesselId == null || !canScopeToVessel)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (category == null) {
                        R.string.category_edit_title_new
                    } else {
                        R.string.category_edit_title_edit
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(requiredLabel(R.string.category_edit_name)) },
                    singleLine = true,
                    supportingText = {
                        if (name.isBlank()) {
                            Text(
                                text = stringResource(R.string.category_edit_name_required),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.TouchTargetPrimary),
                )
                Text(
                    text = stringResource(R.string.category_edit_colour),
                    style = MaterialTheme.typography.labelLarge,
                )
                SwatchRow(selectedArgb = colour, onSelect = { colour = it })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.TouchTargetMin),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.category_edit_global),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.category_edit_global_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isGlobal,
                        onCheckedChange = { isGlobal = it },
                        enabled = canScopeToVessel,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CategoryDraft(
                            id = category?.id,
                            name = name,
                            colorArgb = colour,
                            isGlobal = isGlobal,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
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

private val CATEGORY_DOT = 16.dp
private const val SEPARATOR = " · "

@Preview
@Composable
private fun CategoryManagerPreview() {
    DeckWatchTheme {
        CategoryManagerContent(
            state = CategoryManagerUiState(
                categories = listOf(
                    Category("1", null, "Weekly Round", Swatches.All[3].argb, null, 0),
                    Category("2", "v", "PSC Focus Items", Swatches.All[5].argb, null, 1),
                ),
                isLoading = false,
            ),
        )
    }
}
