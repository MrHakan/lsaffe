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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.deckwatch.core.designsystem.components.ConditionChipRow
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.feature.equipment.components.ConditionUndoBar
import com.deckwatch.feature.equipment.components.DeficiencyFormCard
import com.deckwatch.feature.equipment.components.DeficiencyList
import com.deckwatch.feature.equipment.components.EquipmentIdentity
import com.deckwatch.feature.equipment.components.LabelValue
import com.deckwatch.feature.equipment.components.MonthlyChecklistSection
import com.deckwatch.feature.equipment.components.NextDueRow

/**
 * The equipment bottom sheet — §7.4, and the quick-action condition control of §7.3.
 *
 * The three stages are the sheet's own: it opens at **peek**, the drag handle pulls it to **half**,
 * and the single 48dp *Full record* button — the one primary action of the sheet
 * (DESIGN_OVERHAUL rule 1) — hands over to [EquipmentDetailScreen] for the **full** record.
 *
 * * **Peek** — tag (monospace), type name and symbol; the shared five-grade `ConditionChipRow`
 *   (56dp, rule 5); the next due date as a `DueDeltaChip` (rule 6).
 * * **Half** — location, maker / model / serial, last inspection, open deficiencies, the monthly
 *   checklist, the *Log inspection* / *Take photo* entry points and the destructive actions.
 *
 * Tapping a condition chip writes `condition` and `conditionSetAt` immediately, fires a haptic tick
 * and shows the inline "Graded Good · Undo" confirmation for ten seconds (rules 8 and 10); a grade
 * of `DEFECTIVE` or `OUT_OF_SERVICE` additionally expands a pre-filled deficiency form that the
 * officer may dismiss (§7.3).
 *
 * @param onGraded sweep-mode hook (§7.3): called **after** the grade is written, so the host can
 *   advance to the next unchecked item on the deck without closing the sheet.
 * @param onOpenFullDetail open [EquipmentDetailScreen] for this item — the *Full record* button.
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Dragging the sheet up is itself a request for more detail, and vice versa: the stage follows
    // the handle so there is never a second, contradicting control for the same thing.
    LaunchedEffect(sheetState.currentValue) {
        when (sheetState.currentValue) {
            SheetValue.Expanded -> expanded = true
            SheetValue.PartiallyExpanded -> expanded = false
            SheetValue.Hidden -> Unit
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
                onSelect = { grade ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setCondition(grade) { id, written -> onGraded?.invoke(id, written) }
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

            SheetMessageLine(state.message, viewModel::consumeMessage)

            // ---------------------------------------------------------- HALF
            if (expanded) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
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

                SectionHeader(stringResource(R.string.equip_actions))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpacingL),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                ) {
                    TextButton(
                        onClick = { onMoveToDeck(equipment.id) },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetMin),
                    ) { Text(stringResource(R.string.equip_move_deck)) }
                    TextButton(
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetMin),
                    ) { Text(stringResource(R.string.equip_delete)) }
                }
            }

            // ------------------------------------------ the one primary action
            Button(
                onClick = { onOpenFullDetail(equipment.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM)
                    .heightIn(min = Dimens.TouchTargetMin),
            ) { Text(stringResource(R.string.equip_sheet_full_record)) }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.equip_delete_confirm_title, state.equipment?.tag.orEmpty()),
            body = stringResource(R.string.equip_delete_confirm_body),
            confirmLabel = stringResource(R.string.equip_delete),
            cancelLabel = stringResource(R.string.equip_cancel),
            onConfirm = {
                confirmingDelete = false
                viewModel.delete { id, undo -> onDeleted(id, undo) }
                onDismiss()
            },
            onDismiss = { confirmingDelete = false },
        )
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
                SheetMessage.DUPLICATED -> R.string.equip_duplicated
            },
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
    )
}

private const val MESSAGE_MILLIS = 3_000L
