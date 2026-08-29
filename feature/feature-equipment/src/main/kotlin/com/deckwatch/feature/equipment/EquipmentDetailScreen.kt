@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.equipment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.Dimens
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

/**
 * The complete equipment record on its own screen — the full stage of §7.4 without the sheet.
 *
 * Same view model, same sections, same editable attribute form (§9.3); the difference is room. Photo
 * URIs are listed rather than rendered: image loading arrives with the photo phase, and a
 * placeholder that names the file is more honest than a broken thumbnail.
 */
@Composable
fun EquipmentDetailScreen(
    equipmentId: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: EquipmentSheetViewModel = hiltViewModel()
    LaunchedEffect(equipmentId) { viewModel.bind(equipmentId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var openCard by remember { mutableStateOf<RegulationCard?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.equipment?.tag ?: stringResource(R.string.detail_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpacingL),
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
                onGrade = { grade ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setCondition(grade)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingS))
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
            )
            PhotoSection(equipment.photoUris)
            RequirementsSection(cards = state.requirements, onOpen = { openCard = it })
            Column(modifier = Modifier.padding(bottom = Dimens.SpacingXl)) {}
        }
    }

    openCard?.let { card -> RegulationCardDialog(card = card, onDismiss = { openCard = null }) }
}
