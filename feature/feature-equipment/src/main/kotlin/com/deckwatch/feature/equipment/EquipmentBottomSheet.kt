@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.feature.equipment.components.AttributesSection
import com.deckwatch.feature.equipment.components.ConditionChipRow
import com.deckwatch.feature.equipment.components.DeficiencyFormCard
import com.deckwatch.feature.equipment.components.DeficiencyList
import com.deckwatch.feature.equipment.components.DueLine
import com.deckwatch.feature.equipment.components.EquipmentIdentity
import com.deckwatch.feature.equipment.components.LabelValue
import com.deckwatch.feature.equipment.components.MonthlyChecklistSection
import com.deckwatch.feature.equipment.components.PhotoSection
import com.deckwatch.feature.equipment.components.RegulationCardDialog
import com.deckwatch.feature.equipment.components.RequirementsSection
import com.deckwatch.feature.equipment.components.SectionHeader
import com.deckwatch.feature.equipment.components.TaskListSection

/** The three stages of the equipment sheet — §7.4. */
private enum class SheetStage { PEEK, HALF, FULL }

/**
 * The equipment bottom sheet — §7.4, and the quick-action condition control of §7.3.
 *
 * Three stages of one sheet:
 * * **Peek** — tag (monospace), type name, symbol, the five 56dp condition chips, and the next due
 *   date with a colour-coded day delta.
 * * **Half** — location, maker / model / serial, last inspection, open deficiencies, the monthly
 *   checklist, and the *Log inspection* / *Take photo* entry points.
 * * **Full** — dynamic attributes (§9.3) with an inline editor, the task list with per-task due
 *   dates and status, notes, photos, *Applicable requirements* (§8.4) opening the shared regulation
 *   card in a dialog, and the destructive actions.
 *
 * Tapping a condition chip writes `condition` and `conditionSetAt` immediately, fires a haptic tick,
 * offers a ten-second undo (C10) and — for `DEFECTIVE` or `OUT_OF_SERVICE` — expands a pre-filled
 * deficiency form that the officer may dismiss (§7.3).
 *
 * @param onGraded sweep-mode hook (§7.3): called **after** the grade is written, so the host can
 *   advance to the next unchecked item on the deck without closing the sheet.
 * @param onOpenFullDetail open [EquipmentDetailScreen] for this item.
 * @param onTakePhoto camera entry point; capture belongs to the photo phase, this only surfaces it.
 * @param onLogInspection full inspection logging belongs to `feature-inspection`; the sheet writes
 *   only the unambiguous monthly-checklist completion itself (§9.3).
 * @param onMoveToDeck the host picks the destination deck and calls back into its own move flow.
 * @param onDeleted soft delete has happened; the host shows the ten-second undo snackbar and calls
 *   the supplied lambda if the officer takes it (C10).
 */
@Composable
fun EquipmentBottomSheet(
    equipmentId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onGraded: ((equipmentId: String, grade: ConditionGrade) -> Unit)? = null,
    onOpenFullDetail: (String) -> Unit = {},
    onTakePhoto: (String) -> Unit = {},
    onLogInspection: (String) -> Unit = {},
    onMoveToDeck: (String) -> Unit = {},
    onDeleted: (equipmentId: String, undo: suspend () -> Unit) -> Unit = { _, _ -> },
) {
    val viewModel: EquipmentSheetViewModel = hiltViewModel()
    LaunchedEffect(equipmentId) { viewModel.bind(equipmentId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()
    var stage by rememberSaveable { mutableStateOf(SheetStage.PEEK) }
    var openCard by remember { mutableStateOf<RegulationCard?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Dragging the sheet up is itself a request for more detail.
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.Expanded && stage == SheetStage.PEEK) {
            stage = SheetStage.HALF
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        val equipment = state.equipment
        val type = state.type
        if (equipment == null) {
            Text(
                text = stringResource(
                    if (state.missing) R.string.equip_sheet_not_found else R.string.equip_sheet_loading,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Dimens.SpacingL),
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpacingL)
                .navigationBarsPadding(),
        ) {
            // ---------------------------------------------------------- PEEK
            EquipmentIdentity(
                symbolKey = equipment.symbolKey,
                tag = equipment.tag,
                typeName = type?.let { localised(it.nameEn, it.nameTr) } ?: equipment.typeKey,
                subtitle = equipment.name,
            )

            SectionHeader(stringResource(R.string.equip_condition_title))
            ConditionChipRow(
                selected = equipment.condition,
                onGrade = { grade ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setCondition(grade) { id, written -> onGraded?.invoke(id, written) }
                },
            )
            state.conditionUndo?.let { undo ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.equip_condition_graded, conditionLabel(undo.newGrade)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = viewModel::undoCondition,
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    ) { Text(stringResource(R.string.equip_condition_undo)) }
                }
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

            DueLine(
                dueDate = equipment.nextDueDate,
                todayEpochDay = state.todayEpochDay,
                taskTitle = state.tasks.firstOrNull { it.taskKey == equipment.nextDueTaskKey }
                    ?.let { localised(it.titleEn, it.titleTr) },
            )

            SheetMessageLine(state.message, viewModel::consumeMessage)

            // ---------------------------------------------------------- HALF
            if (stage != SheetStage.PEEK) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingS))
                LabelValue(stringResource(R.string.equip_location), equipment.location)
                LabelValue(stringResource(R.string.equip_maker), equipment.makerName)
                LabelValue(stringResource(R.string.equip_model), equipment.modelName)
                LabelValue(stringResource(R.string.equip_serial), equipment.serialNumber, monospace = true)
                LabelValue(stringResource(R.string.equip_status), statusLabel(equipment.statusFlag))
                LabelValue(
                    label = stringResource(R.string.equip_last_inspection),
                    value = state.lastInspection?.let { formatDate(it) }
                        ?: stringResource(R.string.equip_no_record),
                )
                DeficiencyList(state.openDeficiencies)
                MonthlyChecklistSection(
                    items = state.checklist,
                    complete = state.checklistComplete,
                    canLog = state.monthlyTaskKey != null,
                    onToggle = viewModel::toggleChecklistItem,
                    onLog = viewModel::logMonthlyInspection,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                ) {
                    OutlinedButton(
                        onClick = { onLogInspection(equipment.id) },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                    ) { Text(stringResource(R.string.equip_log_inspection)) }
                    OutlinedButton(
                        onClick = { onTakePhoto(equipment.id) },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                    ) { Text(stringResource(R.string.equip_take_photo)) }
                }
            }

            // ---------------------------------------------------------- FULL
            if (stage == SheetStage.FULL) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingS))
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
                    text = equipment.notes?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.equip_notes_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PhotoSection(equipment.photoUris)
                RequirementsSection(cards = state.requirements, onOpen = { openCard = it })

                SectionHeader(stringResource(R.string.equip_actions))
                DuplicateStepper(onDuplicate = viewModel::duplicate)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                ) {
                    OutlinedButton(
                        onClick = { onMoveToDeck(equipment.id) },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                    ) { Text(stringResource(R.string.equip_move_deck)) }
                    OutlinedButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                    ) { Text(stringResource(R.string.equip_delete)) }
                }
                OutlinedButton(
                    onClick = { onOpenFullDetail(equipment.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.TouchTargetPrimary)
                        .padding(top = Dimens.SpacingS),
                ) { Text(stringResource(R.string.equip_sheet_open_detail)) }
            }

            StageControls(
                stage = stage,
                onStage = { stage = it },
                modifier = Modifier.padding(vertical = Dimens.SpacingM),
            )
        }
    }

    openCard?.let { card ->
        RegulationCardDialog(card = card, onDismiss = { openCard = null })
    }

    if (confirmingDelete) {
        val tag = state.equipment?.tag.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.equip_delete_confirm_title, tag)) },
            text = { Text(stringResource(R.string.equip_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete { id, undo -> onDeleted(id, undo) }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.equip_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.equip_cancel))
                }
            },
        )
    }
}

/** Peek -> half -> full, and back. Every stage is reachable without a drag gesture (§14, C5). */
@Composable
private fun StageControls(
    stage: SheetStage,
    onStage: (SheetStage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
        if (stage != SheetStage.PEEK) {
            OutlinedButton(
                onClick = { onStage(if (stage == SheetStage.FULL) SheetStage.HALF else SheetStage.PEEK) },
                modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
            ) { Text(stringResource(R.string.equip_sheet_show_less)) }
        }
        if (stage != SheetStage.FULL) {
            OutlinedButton(
                onClick = { onStage(if (stage == SheetStage.PEEK) SheetStage.HALF else SheetStage.FULL) },
                modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
            ) {
                Text(
                    stringResource(
                        if (stage == SheetStage.PEEK) R.string.equip_sheet_show_more else R.string.equip_sheet_show_full,
                    ),
                )
            }
        }
    }
}

/** Duplicate ×N — §7.5. The stepper is glove-sized; the ceiling keeps a slip from creating 400 rows. */
@Composable
private fun DuplicateStepper(onDuplicate: (Int) -> Unit, modifier: Modifier = Modifier) {
    var count by rememberSaveable { mutableIntStateOf(1) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        OutlinedButton(
            onClick = { count = (count - 1).coerceAtLeast(1) },
            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
        ) { Text("−") }
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { count = (count + 1).coerceAtMost(AddEquipmentViewModel.MAX_COPIES) },
            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
        ) { Text("+") }
        OutlinedButton(
            onClick = { onDuplicate(count) },
            modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
        ) { Text(stringResource(R.string.equip_duplicate_n, count)) }
    }
}

/** One line of transient feedback; it clears itself so nothing lingers on a sheet left open. */
@Composable
private fun SheetMessageLine(message: SheetMessage?, onConsume: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(MESSAGE_MILLIS)
        onConsume()
    }
    Text(
        text = stringResource(
            when (message) {
                SheetMessage.ATTRIBUTES_SAVED -> R.string.equip_attributes_saved
                SheetMessage.DEFICIENCY_SAVED -> R.string.equip_deficiency_saved
                SheetMessage.MONTHLY_LOGGED -> R.string.equip_monthly_logged
                SheetMessage.MONTHLY_NO_TASK -> R.string.equip_monthly_no_task
                SheetMessage.DUPLICATED -> R.string.equip_duplicate
            },
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.SpacingXs),
    )
}

private const val MESSAGE_MILLIS = 3_000L
