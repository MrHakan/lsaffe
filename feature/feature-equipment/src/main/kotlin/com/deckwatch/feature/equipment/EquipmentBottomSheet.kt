@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.equipment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.ConditionChipRow
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.Zone
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
import com.deckwatch.feature.equipment.photo.PhotoStore
import java.io.File
import kotlinx.coroutines.delay

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
 * @param onMoveToDeck lets a host run its own move flow. Left null, the sheet picks the deck
 *   itself, so an item created from the tab's FAB can be placed without the host doing anything.
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
    onMoveToDeck: ((String) -> Unit)? = null,
    onDeleted: (equipmentId: String, undo: suspend () -> Unit) -> Unit = { _, _ -> },
) {
    val viewModel: EquipmentSheetViewModel = hiltViewModel()
    LaunchedEffect(equipmentId) { viewModel.bind(equipmentId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var movingToDeck by remember { mutableStateOf(false) }
    var moveDeckChoice by remember { mutableStateOf<String?>(null) }
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val zones by viewModel.zonesForMove.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Capture: the file is created before the camera app starts, and only survives if the camera
    // reports success — a cancelled capture leaves an empty file that nothing should record.
    val context = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var settingReminder by remember { mutableStateOf(false) }
    var openCard by remember { mutableStateOf<RegulationCard?>(null) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto
        pendingPhoto = null
        if (file == null) return@rememberLauncherForActivityResult
        if (saved) {
            viewModel.addPhoto(PhotoStore.uriFor(context, file).toString())
        } else {
            file.delete()
        }
    }

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
                        onClick = {
                            onTakePhoto(equipment.id)
                            val file = PhotoStore.newPhotoFile(context, equipment.id, System.currentTimeMillis())
                            pendingPhoto = file
                            // No camera app at all is a normal state on a locked-down phone, and it
                            // arrives as an exception rather than a result — so it is caught here.
                            runCatching { capture.launch(PhotoStore.uriFor(context, file)) }
                                .onFailure {
                                    pendingPhoto = null
                                    file.delete()
                                    cameraUnavailable = true
                                }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                    ) { Text(stringResource(R.string.equip_take_photo)) }
                }

                // The record itself, once the sheet is open all the way. Everything above is what
                // an officer standing at the equipment needs; this is what they need at a desk.
                if (expanded) {
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
                    PhotoSection(
                        photoUris = equipment.photoUris,
                        onRemove = { uri ->
                            viewModel.removePhoto(uri)
                            PhotoStore.delete(context, uri)
                        },
                    )
                    RequirementsSection(cards = state.requirements, onOpen = { openCard = it })
                }

                SectionHeader(stringResource(R.string.equip_actions))
                OutlinedButton(
                    onClick = { settingReminder = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpacingL)
                        .heightIn(min = Dimens.TouchTargetPrimary),
                ) { Text(stringResource(R.string.equip_remind_me)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpacingL),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                ) {
                    TextButton(
                        // A host that owns a move flow runs it; otherwise the sheet asks.
                        onClick = { onMoveToDeck?.invoke(equipment.id) ?: run { movingToDeck = true } },
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

    if (settingReminder) {
        RemindMeDialog(
            onPick = { days ->
                settingReminder = false
                viewModel.remindIn(days)
            },
            onClear = {
                settingReminder = false
                viewModel.cancelReminder()
            },
            onDismiss = { settingReminder = false },
        )
    }

    if (cameraUnavailable) {
        AlertDialog(
            onDismissRequest = { cameraUnavailable = false },
            title = { Text(stringResource(R.string.equip_photo_no_camera_title)) },
            text = { Text(stringResource(R.string.equip_photo_no_camera_body)) },
            confirmButton = {
                TextButton(onClick = { cameraUnavailable = false }) {
                    Text(stringResource(R.string.equip_photo_no_camera_dismiss))
                }
            },
        )
    }

    if (movingToDeck) {
        val chosenDeck = moveDeckChoice
        if (chosenDeck == null) {
            MoveToDeckDialog(
                decks = decks,
                currentDeckId = state.equipment?.deckId,
                onPick = { deckId ->
                    if (deckId == null) {
                        // Landed for service: no deck, so no zone to ask about.
                        movingToDeck = false
                        viewModel.moveToDeck(null)
                    } else {
                        moveDeckChoice = deckId
                        viewModel.selectDeckForMove(deckId)
                    }
                },
                onDismiss = { movingToDeck = false },
            )
        } else {
            MoveToZoneDialog(
                zones = zones,
                currentZoneId = state.equipment?.zoneId,
                onPick = { zoneId ->
                    movingToDeck = false
                    moveDeckChoice = null
                    viewModel.selectDeckForMove(null)
                    viewModel.moveToDeck(chosenDeck, zoneId)
                },
                onBack = {
                    moveDeckChoice = null
                    viewModel.selectDeckForMove(null)
                },
            )
        }
    }

    // The rule behind a requirement, read without leaving the equipment it was raised against.
    openCard?.let { card ->
        RegulationCardDialog(card = card, onDismiss = { openCard = null })
    }
}

/**
 * "Remind me in…" — §11.3.
 *
 * Three horizons, no free-form date: a reminder is a nudge before the next port call, not a second
 * scheduling system competing with the due engine. Clearing is offered in the same place, because
 * an armed reminder is otherwise invisible until it fires.
 */
@Composable
private fun RemindMeDialog(onPick: (Int) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.equip_remind_title)) },
        text = {
            Column {
                DeckChoiceRow(
                    label = stringResource(R.string.equip_remind_tomorrow),
                    selected = false,
                    onClick = { onPick(REMIND_TOMORROW_DAYS) },
                )
                DeckChoiceRow(
                    label = stringResource(R.string.equip_remind_three_days),
                    selected = false,
                    onClick = { onPick(REMIND_THREE_DAYS) },
                )
                DeckChoiceRow(
                    label = stringResource(R.string.equip_remind_week),
                    selected = false,
                    onClick = { onPick(REMIND_WEEK_DAYS) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClear) { Text(stringResource(R.string.equip_remind_clear)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.equip_cancel)) }
        },
    )
}

private const val REMIND_TOMORROW_DAYS = 1
private const val REMIND_THREE_DAYS = 3
private const val REMIND_WEEK_DAYS = 7

/**
 * Second step of the move: which zone of that deck — §6.4. Always offers "no zone", because a deck
 * without drawn zones is normal and an item does not have to sit in one.
 */
@Composable
private fun MoveToZoneDialog(
    zones: List<Zone>,
    currentZoneId: String?,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(stringResource(R.string.equip_move_zone_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DeckChoiceRow(
                    label = stringResource(R.string.equip_move_zone_none),
                    selected = currentZoneId == null,
                    onClick = { onPick(null) },
                )
                zones.forEach { zone ->
                    DeckChoiceRow(
                        label = zone.name,
                        selected = zone.id == currentZoneId,
                        onClick = { onPick(zone.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.equip_move_zone_back)) }
        },
    )
}

/**
 * Where does this item live? — §6.5.
 *
 * The list is the vessel's decks in stack order plus "unplaced", which is where an item goes when
 * it is landed for service. Zones are not offered: a zone belongs to a deck plan, and picking one
 * without seeing the plan would be guessing.
 */
@Composable
private fun MoveToDeckDialog(
    decks: List<Deck>,
    currentDeckId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.equip_move_deck_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (decks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.equip_move_deck_none),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                decks.forEach { deck ->
                    DeckChoiceRow(
                        label = deck.name,
                        selected = deck.id == currentDeckId,
                        onClick = { onPick(deck.id) },
                    )
                }
                DeckChoiceRow(
                    label = stringResource(R.string.equip_move_deck_unplaced),
                    selected = currentDeckId == null,
                    onClick = { onPick(null) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.equip_cancel)) }
        },
    )
}

@Composable
private fun DeckChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
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
