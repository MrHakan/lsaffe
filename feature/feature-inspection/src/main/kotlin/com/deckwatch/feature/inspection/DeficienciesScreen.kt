package com.deckwatch.feature.inspection

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.components.ConditionDot
import com.deckwatch.core.designsystem.components.DateField
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.DeficiencyStatusChip
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SeverityChip
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity

/**
 * Open and closed deficiencies — §6.8.
 *
 * Severity-sorted, with one primary action ("Raise deficiency") and an edit dialog that records the
 * corrective action and target date — the target date through the shared [DateField], never typed
 * (DESIGN_OVERHAUL rule 4) — and closes the finding with a name and a date.
 */
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
            DeckWatchTopBar(
                title = stringResource(R.string.deficiencies_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.insp_action_back),
            )
        },
        floatingActionButton = {
            if (state.hasVessel) {
                ExtendedFloatingActionButton(
                    onClick = { raising = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.deficiencies_new)) },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!state.hasVessel) {
                EmptyState(
                    icon = Icons.Filled.DirectionsBoat,
                    title = stringResource(R.string.due_no_vessel_title),
                    body = stringResource(R.string.due_no_vessel),
                )
                return@Column
            }
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    text = { Text("${stringResource(R.string.deficiencies_tab_open)}  ${state.open.size}") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    text = { Text("${stringResource(R.string.deficiencies_tab_closed)}  ${state.closed.size}") },
                )
            }
            val rows = if (tab == 0) state.open else state.closed
            when {
                rows.isEmpty() && tab == 0 -> EmptyState(
                    icon = Icons.Filled.ReportProblem,
                    title = stringResource(R.string.deficiencies_empty_open_title),
                    body = stringResource(R.string.deficiencies_empty_open),
                    actionLabel = stringResource(R.string.deficiencies_new),
                    onAction = { raising = true },
                )

                rows.isEmpty() -> EmptyState(
                    icon = Icons.Filled.DoneAll,
                    title = stringResource(R.string.deficiencies_empty_closed_title),
                    body = stringResource(R.string.deficiencies_empty_closed),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.deficiency.id }) { row ->
                        DeficiencyListRow(row = row, onClick = { editing = row.deficiency.id })
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

/** One finding on the shared row: severity dot or symbol, the finding, the two status chips. */
@Composable
private fun DeficiencyListRow(row: DeficiencyRow, onClick: () -> Unit) {
    val deficiency = row.deficiency
    val meta = listOfNotNull(
        row.equipmentTag,
        stringResource(R.string.deficiency_raised, Dates.formatIso(deficiency.raisedDate)),
        deficiency.targetDate?.let { stringResource(R.string.deficiency_target, Dates.formatIso(it)) },
        deficiency.closedDate?.let { stringResource(R.string.deficiency_closed, Dates.formatIso(it)) },
    ).joinToString(" · ")
    DeckWatchListRow(
        title = deficiency.title,
        subtitle = meta,
        onClick = onClick,
        leading = {
            val symbolKey = row.symbolKey
            if (symbolKey != null) {
                SymbolTile(symbolKey = symbolKey, size = SymbolSize)
            } else {
                ConditionDot(grade = deficiency.severity.asGrade(), size = SeverityDotSize)
            }
        },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                SeverityChip(severity = deficiency.severity, text = labelOf(deficiency.severity))
                DeficiencyStatusChip(status = deficiency.status, text = labelOf(deficiency.status))
            }
        },
    )
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
    var targetDate by rememberSaveable { mutableStateOf(initial.targetDate) }
    var severity by rememberSaveable { mutableStateOf(initial.severity) }
    var status by rememberSaveable { mutableStateOf(initial.status) }
    var equipmentId by rememberSaveable { mutableStateOf(initial.equipmentId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.deficiencies_new else R.string.deficiencies_edit_title,
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
                            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                        )
                    }
                }
                LabelledRow(text = stringResource(R.string.deficiency_field_status)) {
                    DeficiencyStatus.entries.forEach { entry ->
                        FilterChip(
                            selected = entry == status,
                            onClick = { status = entry },
                            label = { Text(labelOf(entry)) },
                            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
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
                DateField(
                    label = stringResource(R.string.deficiency_field_target),
                    epochDay = targetDate,
                    onChange = { targetDate = it },
                    labels = dateFieldLabels(),
                    modifier = Modifier.padding(top = Dimens.SpacingS),
                )
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
                        modifier = Modifier
                            .padding(top = Dimens.SpacingS)
                            .heightIn(min = Dimens.TouchTargetMin),
                    ) {
                        Text(stringResource(R.string.deficiency_close))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.insp_action_cancel))
            }
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

/**
 * The severity ramp reuses the condition palette — §14 fixes one set of semantic colours, and
 * `ConditionDot` is the shared way to show it in a list.
 */
private fun Severity.asGrade(): ConditionGrade = when (this) {
    Severity.OBSERVATION -> ConditionGrade.NOT_CHECKED
    Severity.MINOR -> ConditionGrade.MONITOR
    Severity.MAJOR -> ConditionGrade.DEFECTIVE
    Severity.CRITICAL_DETAINABLE -> ConditionGrade.OUT_OF_SERVICE
}

private val EditorMaxHeight = 460.dp
private val SymbolSize = 40.dp
private val SeverityDotSize = 16.dp
