package com.deckwatch.feature.inspection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity

/**
 * Open and closed deficiencies — §6.8.
 *
 * Severity-sorted, colour-coded through `ConditionColors.of(Severity)`, with an edit dialog that
 * records the corrective action and target date and closes the finding with a name and a date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeficienciesScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DeficienciesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var raising by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deficiencies_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.insp_action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { raising = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.deficiencies_new)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("${stringResource(R.string.deficiencies_tab_open)}  ${state.open.size}") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("${stringResource(R.string.deficiencies_tab_closed)}  ${state.closed.size}") },
                )
            }
            val rows = if (tab == 0) state.open else state.closed
            when {
                !state.hasVessel -> EmptyHint(text = stringResource(R.string.due_no_vessel))
                rows.isEmpty() -> EmptyHint(
                    text = stringResource(
                        if (tab == 0) R.string.deficiencies_empty_open else R.string.deficiencies_empty_closed,
                    ),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.deficiency.id }) { row ->
                        DeficiencyRowContent(row = row, onClick = { editing = row.deficiency.id })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val editingId = editing
    val existing = (state.open + state.closed).firstOrNull { it.deficiency.id == editingId }?.deficiency
    if (existing != null) {
        DeficiencyEditorDialog(
            initial = existing.toDraft(),
            equipmentOptions = state.equipmentOptions,
            existing = existing,
            today = state.today,
            onDismiss = { editing = null },
            onSave = { draft ->
                viewModel.update(existing, draft)
                editing = null
            },
            onClose = { closedBy, closedDate ->
                viewModel.close(existing, closedBy, closedDate)
                editing = null
            },
        )
    }

    if (raising) {
        DeficiencyEditorDialog(
            initial = DeficiencyDraft(),
            equipmentOptions = state.equipmentOptions,
            existing = null,
            today = state.today,
            onDismiss = { raising = false },
            onSave = { draft ->
                viewModel.raise(draft)
                raising = false
            },
            onClose = { _, _ -> raising = false },
        )
    }
}

@Composable
private fun DeficiencyRowContent(row: DeficiencyRow, onClick: () -> Unit) {
    val deficiency = row.deficiency
    val severityColor = ConditionColors.of(deficiency.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.ListRowComfortable)
            .padding(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusRail(color = severityColor)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoChip(
                    text = labelOf(deficiency.severity),
                    color = severityColor.copy(alpha = 0.18f),
                    contentColor = severityColor,
                )
                row.equipmentTag?.let { TagText(tag = it) }
                InfoChip(text = labelOf(deficiency.status))
            }
            Text(
                text = deficiency.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                Text(
                    text = stringResource(R.string.deficiency_raised, Dates.formatIso(deficiency.raisedDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                deficiency.targetDate?.let {
                    Text(
                        text = stringResource(R.string.deficiency_target, Dates.formatIso(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                deficiency.closedDate?.let {
                    Text(
                        text = stringResource(R.string.deficiency_closed, Dates.formatIso(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = ConditionColors.Good,
                    )
                }
            }
        }
    }
}

/** Raise-new and edit are the same form; [existing] decides whether the close block is offered. */
@Composable
private fun DeficiencyEditorDialog(
    initial: DeficiencyDraft,
    equipmentOptions: List<FilterOption>,
    existing: Deficiency?,
    today: Long,
    onDismiss: () -> Unit,
    onSave: (DeficiencyDraft) -> Unit,
    onClose: (closedBy: String, closedDate: Long) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var corrective by rememberSaveable { mutableStateOf(initial.correctiveAction.orEmpty()) }
    var raisedBy by rememberSaveable { mutableStateOf(initial.raisedBy) }
    var spare by rememberSaveable { mutableStateOf(initial.sparePartRequired.orEmpty()) }
    var closedBy by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable {
        mutableStateOf(initial.targetDate?.let { Dates.formatIso(it) }.orEmpty())
    }
    var severity by rememberSaveable { mutableStateOf(initial.severity) }
    var status by rememberSaveable { mutableStateOf(initial.status) }
    var equipmentId by rememberSaveable { mutableStateOf(initial.equipmentId) }

    val targetDate = targetText.takeIf { it.isNotBlank() }?.let { parseIsoDate(it) }
    val targetValid = targetText.isBlank() || targetDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.deficiencies_new else R.string.deficiencies_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = EditorMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                DialogField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.deficiency_field_title),
                )
                DialogField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.deficiency_field_description),
                    singleLine = false,
                )
                LabelledRow(text = stringResource(R.string.deficiency_field_severity)) {
                    Severity.entries.forEach { entry ->
                        FilterChip(
                            selected = entry == severity,
                            onClick = { severity = entry },
                            label = { Text(labelOf(entry)) },
                        )
                    }
                }
                LabelledRow(text = stringResource(R.string.deficiency_field_status)) {
                    DeficiencyStatus.entries.forEach { entry ->
                        FilterChip(
                            selected = entry == status,
                            onClick = { status = entry },
                            label = { Text(labelOf(entry)) },
                        )
                    }
                }
                LabelledRow(text = stringResource(R.string.deficiency_equipment)) {
                    FilterDropdownChip(
                        label = stringResource(R.string.deficiency_no_equipment),
                        selectedLabel = equipmentOptions.firstOrNull { it.id == equipmentId }?.label,
                        options = equipmentOptions,
                        optionLabel = { it.label },
                        onSelect = { equipmentId = it?.id },
                    )
                }
                DialogField(
                    value = corrective,
                    onValueChange = { corrective = it },
                    label = stringResource(R.string.deficiency_field_corrective),
                    singleLine = false,
                )
                DialogField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = stringResource(R.string.deficiency_field_target),
                )
                if (!targetValid) {
                    Text(
                        text = stringResource(R.string.due_complete_bad_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DialogField(
                    value = spare,
                    onValueChange = { spare = it },
                    label = stringResource(R.string.deficiency_field_spare),
                )
                DialogField(
                    value = raisedBy,
                    onValueChange = { raisedBy = it },
                    label = stringResource(R.string.deficiency_field_raised_by),
                )
                if (existing != null && existing.status != DeficiencyStatus.CLOSED) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingM))
                    DialogField(
                        value = closedBy,
                        onValueChange = { closedBy = it },
                        label = stringResource(R.string.deficiency_field_closed_by),
                    )
                    TextButton(
                        enabled = closedBy.isNotBlank(),
                        onClick = { onClose(closedBy, today) },
                        modifier = Modifier.padding(top = Dimens.SpacingS),
                    ) {
                        Text(stringResource(R.string.deficiency_close))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && targetValid,
                onClick = {
                    onSave(
                        DeficiencyDraft(
                            id = existing?.id,
                            equipmentId = equipmentId,
                            severity = severity,
                            title = title,
                            description = description,
                            correctiveAction = corrective,
                            targetDate = targetDate,
                            raisedBy = raisedBy,
                            status = status,
                            sparePartRequired = spare,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.insp_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.insp_action_cancel)) }
        },
    )
}

@Composable
private fun LabelledRow(text: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = Dimens.SpacingM)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

internal fun Deficiency.toDraft(): DeficiencyDraft = DeficiencyDraft(
    id = id,
    equipmentId = equipmentId,
    severity = severity,
    title = title,
    description = description,
    correctiveAction = correctiveAction,
    targetDate = targetDate,
    raisedBy = raisedBy,
    status = status,
    sparePartRequired = sparePartRequired,
)

private val EditorMaxHeight = 460.dp
