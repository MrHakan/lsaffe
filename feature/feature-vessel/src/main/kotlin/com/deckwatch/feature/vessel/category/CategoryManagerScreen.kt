package com.deckwatch.feature.vessel.category

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Category
import com.deckwatch.feature.vessel.R
import com.deckwatch.feature.vessel.common.ConfirmDialog
import com.deckwatch.feature.vessel.common.SwatchRow
import com.deckwatch.feature.vessel.common.Swatches
import com.deckwatch.feature.vessel.common.TeachingEmptyState
import com.deckwatch.feature.vessel.common.VesselTopBar

/**
 * CRUD over the logical categories of §6.4. A null [vesselId] resolves to the active vessel; a
 * category can be scoped to that vessel or made global.
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
            message = stringResource(R.string.category_manager_delete_message, name),
            confirmLabel = stringResource(R.string.vessel_action_delete),
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
                VesselTopBar(
                    title = stringResource(R.string.category_manager_title),
                    onBack = onBack,
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
                TeachingEmptyState(
                    message = stringResource(R.string.category_manager_empty_message),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowCompact)
            .clickable(onClick = onEdit)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        Box(
            modifier = Modifier
                .size(CATEGORY_DOT)
                .clip(CircleShape)
                .background(Color(category.colorArgb)),
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = onEdit,
            label = {
                Text(
                    stringResource(
                        if (category.vesselId == null) {
                            R.string.category_scope_global
                        } else {
                            R.string.category_scope_vessel
                        },
                    ),
                )
            },
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTargetMin)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.vessel_action_delete),
            )
        }
    }
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
    var showNameError by remember(category) { mutableStateOf(false) }

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
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    label = { Text(stringResource(R.string.category_edit_name)) },
                    isError = showNameError,
                    singleLine = true,
                    supportingText = {
                        if (showNameError) {
                            Text(
                                text = stringResource(R.string.category_edit_name_required),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                    if (name.isBlank()) {
                        showNameError = true
                    } else {
                        onSave(
                            CategoryDraft(
                                id = category?.id,
                                name = name,
                                colorArgb = colour,
                                isGlobal = isGlobal,
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

private val CATEGORY_DOT = 16.dp

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
