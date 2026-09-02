@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.deckwatch.core.designsystem.components.ConditionChipRow
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.feature.equipment.components.AttributesSection
import com.deckwatch.feature.equipment.components.ConditionUndoBar
import com.deckwatch.feature.equipment.components.DeficiencyFormCard
import com.deckwatch.feature.equipment.components.DeficiencyList
import com.deckwatch.feature.equipment.components.EquipmentIdentity
import com.deckwatch.feature.equipment.components.LabelValue
import com.deckwatch.feature.equipment.components.MonthlyChecklistSection
import com.deckwatch.feature.equipment.components.NextDueRow
import com.deckwatch.feature.equipment.components.PhotoSection
import com.deckwatch.feature.equipment.components.RegulationCardDialog
import com.deckwatch.feature.equipment.components.RequirementsSection
import com.deckwatch.feature.equipment.components.TaskListSection

/**
 * The complete equipment record on its own screen — the **full** stage of §7.4, one tap from the
 * sheet's *Full record* button.
 *
 * Same view model, same sections, same editable attribute form (§9.3); the difference is room and
 * the top bar. Chrome is the design system's [DeckWatchTopBar] (rule 2) with back plus a single
 * overflow carrying the three record-level actions — Duplicate, Move, Delete — so no destructive
 * button ever competes with the screen's content (rule 1). Delete confirms and then undoes
 * (rule 8): the record is soft-deleted and a ten-second *Undo* snackbar puts it back.
 *
 * Photo URIs are listed rather than rendered: image loading arrives with the photo phase, and a
 * placeholder that names the file is more honest than a broken thumbnail.
 *
 * @param onMoveToDeck the host picks the destination deck and calls back into its own move flow.
 * @param onDeleted soft delete has happened; the host may navigate away and offer its own undo. The
 *   screen shows the ten-second undo itself, so a host that does nothing still loses nothing (C10).
 */
@Composable
fun EquipmentDetailScreen(
    equipmentId: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    onMoveToDeck: (String) -> Unit = {},
    onDeleted: (equipmentId: String, undo: suspend () -> Unit) -> Unit = { _, _ -> },
) {
    val viewModel: EquipmentSheetViewModel = hiltViewModel()
    LaunchedEffect(equipmentId) { viewModel.bind(equipmentId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    var openCard by remember { mutableStateOf<RegulationCard?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var duplicating by remember { mutableStateOf(false) }

    val deletedMessage = stringResource(R.string.equip_deleted)
    val undoLabel = stringResource(R.string.equip_condition_undo)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            DeckWatchTopBar(
                title = state.equipment?.tag ?: stringResource(R.string.detail_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.detail_back),
                subtitle = state.type?.let { localised(it.nameEn, it.nameTr) },
                actions = {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.detail_more_actions),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.equip_duplicate)) },
                            onClick = {
                                menuOpen = false
                                duplicating = true
                            },
                            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.equip_move_deck)) },
                            onClick = {
                                menuOpen = false
                                state.equipment?.let { onMoveToDeck(it.id) }
                            },
                            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.equip_delete)) },
                            onClick = {
                                menuOpen = false
                                confirmingDelete = true
                            },
                            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val equipment = state.equipment
        if (equipment == null) {
            Text(
                text = stringResource(
                    if (state.missing) R.string.equip_sheet_not_found else R.string.equip_sheet_loading,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(padding).padding(Dimens.SpacingL),
            )
            return@Scaffold
        }
        val type = state.type

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            EquipmentIdentity(
                symbolKey = equipment.symbolKey,
                tag = equipment.tag,
                typeName = type?.let { localised(it.nameEn, it.nameTr) } ?: equipment.typeKey,
                subtitle = equipment.name,
            )

            SectionHeader(stringResource(R.string.equip_condition_title))
            ConditionChipRow(
                selected = equipment.condition,
                onSelect = { grade ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setCondition(grade)
                },
                labels = conditionLabels(),
                modifier = Modifier.padding(horizontal = Dimens.SpacingL),
            )
            state.conditionUndo?.let { undo ->
                ConditionUndoBar(undo = undo, onUndo = viewModel::undoCondition)
            }
            state.deficiencyDraft?.let { draft ->
                DeficiencyFormCard(
                    draft = draft,
                    onTitleChange = viewModel::updateDeficiencyTitle,
                    onDescriptionChange = viewModel::updateDeficiencyDescription,
                    onRaisedByChange = viewModel::updateDeficiencyRaisedBy,
                    onSeverityChange = viewModel::updateDeficiencySeverity,
                    onSave = viewModel::saveDeficiency,
                    onDismiss = viewModel::dismissDeficiency,
                )
            }

            NextDueRow(
                dueDate = equipment.nextDueDate,
                todayEpochDay = state.todayEpochDay,
                taskTitle = state.tasks.firstOrNull { it.taskKey == equipment.nextDueTaskKey }
                    ?.let { localised(it.titleEn, it.titleTr) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingS))
            SectionHeader(stringResource(R.string.attr_group_identification))
            LabelValue(stringResource(R.string.equip_location), equipment.location)
            LabelValue(stringResource(R.string.equip_maker), equipment.makerName)
            LabelValue(stringResource(R.string.equip_model), equipment.modelName)
            LabelValue(stringResource(R.string.equip_serial), equipment.serialNumber, monospace = true)
            LabelValue(stringResource(R.string.equip_type_approval), equipment.typeApprovalNumber, monospace = true)
            LabelValue(stringResource(R.string.equip_quantity), equipment.quantity.toString())
            LabelValue(stringResource(R.string.equip_status), statusLabel(equipment.statusFlag))
            LabelValue(
                label = stringResource(R.string.equip_last_inspection),
                value = state.lastInspection?.let { formatDate(it) } ?: stringResource(R.string.equip_no_record),
            )

            DeficiencyList(state.openDeficiencies)
            MonthlyChecklistSection(
                items = state.checklist,
                complete = state.checklistComplete,
                canLog = state.monthlyTaskKey != null,
                onToggle = viewModel::toggleChecklistItem,
                onLog = viewModel::logMonthlyInspection,
            )
            AttributesSection(
                schema = type?.attributeSchema.orEmpty(),
                values = state.attributeValues,
                editorValues = state.editor?.values,
                errors = state.editor?.errors.orEmpty(),
                onStartEditing = viewModel::startEditingAttributes,
                onValueChange = viewModel::updateAttribute,
                onSave = viewModel::saveAttributes,
                onCancel = viewModel::cancelEditingAttributes,
            )
            TaskListSection(tasks = state.tasks, todayEpochDay = state.todayEpochDay)
            SectionHeader(stringResource(R.string.equip_notes))
            Text(
                text = equipment.notes?.takeIf { it.isNotBlank() } ?: stringResource(R.string.equip_notes_none),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Dimens.SpacingL),
            )
            PhotoSection(equipment.photoUris)
            RequirementsSection(cards = state.requirements, onOpen = { openCard = it })
            Column(modifier = Modifier.padding(bottom = Dimens.SpacingXl)) {}
        }
    }

    openCard?.let { card -> RegulationCardDialog(card = card, onDismiss = { openCard = null }) }

    if (duplicating) {
        DuplicateDialog(
            onDismiss = { duplicating = false },
            onDuplicate = { count ->
                duplicating = false
                viewModel.duplicate(count)
            },
        )
    }

    if (confirmingDelete) {
        val tag = state.equipment?.tag.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.equip_delete_confirm_title, tag),
            body = stringResource(R.string.equip_delete_confirm_body),
            confirmLabel = stringResource(R.string.equip_delete),
            cancelLabel = stringResource(R.string.equip_cancel),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete { id, undo ->
                    onDeleted(id, undo)
                    scope.launch {
                        // Ten seconds of grace, on the screen the officer is already looking at (C10).
                        val result = snackbars.showSnackbar(
                            message = deletedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) undo()
                    }
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * Duplicate ×N — §7.5, as a stepper whose confirm button says exactly what it will do.
 *
 * The ceiling keeps a slip from creating four hundred rows; the floor keeps "duplicate" meaning at
 * least one copy.
 */
@Composable
private fun DuplicateDialog(onDismiss: () -> Unit, onDuplicate: (Int) -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.equip_duplicate)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
            ) {
                IconButton(
                    onClick = { count = (count - 1).coerceAtLeast(1) },
                    modifier = Modifier.size(Dimens.TouchTargetMin),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.add_decrease))
                }
                Text(text = count.toString(), style = MaterialTheme.typography.titleLarge)
                IconButton(
                    onClick = { count = (count + 1).coerceAtMost(AddEquipmentViewModel.MAX_COPIES) },
                    modifier = Modifier.size(Dimens.TouchTargetMin),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_increase))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDuplicate(count) },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) { Text(stringResource(R.string.equip_duplicate_n, count)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Text(stringResource(R.string.equip_cancel))
            }
        },
    )
}
