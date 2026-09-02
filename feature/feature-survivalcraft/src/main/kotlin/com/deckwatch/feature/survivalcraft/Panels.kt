package com.deckwatch.feature.survivalcraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.components.ConditionDot
import com.deckwatch.core.designsystem.components.DateField
import com.deckwatch.core.designsystem.components.DateFieldLabels
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DueDeltaChip
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.components.TaskStatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.feature.survivalcraft.drill.DrillRecord
import com.deckwatch.feature.survivalcraft.inventory.InventoryExpirySummary
import com.deckwatch.feature.survivalcraft.inventory.InventoryItem

// ---------------------------------------------------------------------------- components

@Composable
internal fun ComponentsPanel(
    state: SurvivalCraftUiState,
    onOpenChild: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.components.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Widgets,
            title = stringResource(R.string.sc_components_empty_title),
            body = stringResource(R.string.sc_components_empty_body),
            actionLabel = stringResource(R.string.sc_add_component),
            onAction = onAdd,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        state.components.forEach { row ->
            DeckWatchListRow(
                title = row.tag,
                titleIsTag = true,
                subtitle = listOfNotNull(
                    localised(row.typeNameEn, row.typeNameTr),
                    row.hotspotLabelEn?.let { localised(it, row.hotspotLabelTr.orEmpty()) },
                ).joinToString(" · "),
                onClick = { onOpenChild(row.id) },
                leading = { ConditionDot(grade = row.condition, size = 12.dp) },
                trailing = {
                    val due = row.nextDueDate
                    if (due != null) {
                        DueDeltaChip(
                            daysUntilDue = due - state.todayEpochDay,
                            text = dueDeltaText(due, state.todayEpochDay),
                        )
                    } else {
                        StatusChip(
                            text = conditionLabel(row.condition),
                            color = ConditionColors.of(row.condition),
                        )
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

// ---------------------------------------------------------------------------- inventory

@Composable
internal fun InventoryPanel(
    state: SurvivalCraftUiState,
    onQuantity: (String, Int) -> Unit,
    onExpiry: (String, Long?) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        InventorySummaryCard(state.inventorySummary, state.todayEpochDay)
        Text(
            text = stringResource(R.string.sc_inventory_caveat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        )
        if (state.inventory.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Inventory2,
                title = stringResource(R.string.sc_inventory_empty_title),
                body = stringResource(R.string.sc_inventory_empty_body),
            )
            return@Column
        }
        state.inventory.forEach { row ->
            InventoryRow(
                row = row,
                todayEpochDay = state.todayEpochDay,
                onQuantity = { onQuantity(row.item.key, it) },
                onExpiry = { onExpiry(row.item.key, it) },
                onRemove = { onRemove(row.item.key) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun InventorySummaryCard(summary: InventoryExpirySummary, todayEpochDay: Long) {
    Card(modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingL)) {
        Column(modifier = Modifier.padding(Dimens.SpacingL)) {
            Text(
                text = stringResource(R.string.sc_inventory_expiries),
                style = MaterialTheme.typography.titleSmall,
            )
            val text = when {
                summary.tracked == 0 -> stringResource(R.string.sc_inventory_none_tracked)
                summary.expired > 0 -> stringResource(R.string.sc_inventory_expired, summary.expired)
                summary.dueWithinLeadTime > 0 ->
                    stringResource(R.string.sc_inventory_due_soon, summary.dueWithinLeadTime)
                else -> stringResource(R.string.sc_inventory_all_in_date, summary.tracked)
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            val soonest = summary.soonestEpochDay
            if (soonest != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                    modifier = Modifier.padding(top = Dimens.SpacingS),
                ) {
                    Text(
                        text = stringResource(R.string.sc_inventory_soonest, formatDate(soonest)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DueDeltaChip(
                        daysUntilDue = soonest - todayEpochDay,
                        text = dueDeltaText(soonest, todayEpochDay),
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(
    row: InventoryRowUi,
    todayEpochDay: Long,
    onQuantity: (Int) -> Unit,
    onExpiry: (Long?) -> Unit,
    onRemove: () -> Unit,
) {
    val item: InventoryItem = row.item
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = inventoryItemLabel(item.key, item.label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (item.required > 0) {
                    Text(
                        text = stringResource(R.string.sc_inventory_required, item.required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val expiry = item.expiryEpochDay
            if (expiry != null) {
                DueDeltaChip(
                    daysUntilDue = expiry - todayEpochDay,
                    text = dueDeltaText(expiry, todayEpochDay),
                )
            }
            if (row.userAdded) {
                IconButton(onClick = onRemove, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.sc_inventory_remove),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = if (item.quantity == 0) "" else item.quantity.toString(),
                onValueChange = { text -> onQuantity(text.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0) },
                label = { Text(stringResource(R.string.sc_inventory_quantity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
            )
            if (row.expires) {
                DateField(
                    label = stringResource(R.string.sc_inventory_expiry),
                    epochDay = item.expiryEpochDay,
                    onChange = onExpiry,
                    labels = dateFieldLabels(),
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------- tasks

@Composable
internal fun TasksPanel(
    state: SurvivalCraftUiState,
    onLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.taskGroups.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Schedule,
            title = stringResource(R.string.sc_tasks_empty_title),
            body = stringResource(R.string.sc_tasks_empty_body),
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        state.taskGroups.forEach { group ->
            SectionHeader(taskGroupLabel(group.group))
            group.rows.forEach { row ->
                DeckWatchListRow(
                    title = localised(row.titleEn, row.titleTr),
                    subtitle = stringResource(R.string.sc_task_due, formatDate(row.dueDate)) +
                        if (row.needsProvider) " · " + stringResource(R.string.sc_task_provider) else "",
                    onClick = { onLog(row.instanceId) },
                    trailing = {
                        if (row.status == TaskStatus.DONE) {
                            TaskStatusChip(status = row.status, text = taskStatusLabel(row.status))
                        } else {
                            DueDeltaChip(
                                daysUntilDue = row.dueDate - state.todayEpochDay,
                                text = dueDeltaText(row.dueDate, state.todayEpochDay),
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun TaskCompletionForm(
    draft: TaskCompletionDraft,
    onChange: ((TaskCompletionDraft) -> TaskCompletionDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(Dimens.SpacingL)) {
        Column(
            modifier = Modifier.padding(Dimens.SpacingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            Text(
                text = localised(draft.titleEn, draft.titleTr),
                style = MaterialTheme.typography.titleMedium,
            )
            DateField(
                label = stringResource(R.string.sc_completed_date),
                epochDay = draft.completedDate,
                onChange = { day -> onChange { it.copy(completedDate = day) } },
                labels = dateFieldLabels(),
                required = true,
            )
            OutlinedTextField(
                value = draft.completedBy,
                onValueChange = { value -> onChange { it.copy(completedBy = value) } },
                label = { Text(stringResource(R.string.sc_completed_by) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
            OutlinedTextField(
                value = draft.serviceProvider,
                onValueChange = { value -> onChange { it.copy(serviceProvider = value) } },
                label = { Text(stringResource(R.string.sc_service_provider)) },
                supportingText = if (draft.needsProvider) {
                    { Text(stringResource(R.string.sc_provider_hint)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
            OutlinedTextField(
                value = draft.certificateNumber,
                onValueChange = { value -> onChange { it.copy(certificateNumber = value) } },
                label = { Text(stringResource(R.string.sc_certificate_number)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
            OutlinedTextField(
                value = draft.findings,
                onValueChange = { value -> onChange { it.copy(findings = value) } },
                label = { Text(stringResource(R.string.sc_findings)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
            FormButtons(
                saveLabel = stringResource(R.string.sc_save),
                saveEnabled = draft.isValid,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }
}

// ---------------------------------------------------------------------------- drill log

@Composable
internal fun DrillPanel(
    state: SurvivalCraftUiState,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DrillSummaryCard(state)
        if (state.drills.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Sailing,
                title = stringResource(R.string.sc_drill_empty_title),
                body = stringResource(R.string.sc_drill_empty_body),
                actionLabel = stringResource(R.string.sc_drill_record),
                onAction = onRecord,
            )
            return@Column
        }
        state.drills.forEach { record -> DrillRow(record) }
    }
}

@Composable
private fun DrillSummaryCard(state: SurvivalCraftUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(Dimens.SpacingL)) {
        Column(modifier = Modifier.padding(Dimens.SpacingL)) {
            Text(
                text = stringResource(R.string.sc_drill_days_since_launch),
                style = MaterialTheme.typography.titleSmall,
            )
            val days = state.daysSinceLastLaunch
            Text(
                text = if (days == null) {
                    stringResource(R.string.sc_drill_never_launched)
                } else {
                    stringResource(R.string.sc_drill_days, days)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            val last = state.lastDrillDay
            if (last != null) {
                Text(
                    text = stringResource(R.string.sc_drill_last, formatDate(last)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DrillRow(record: DrillRecord) {
    DeckWatchListRow(
        title = formatDate(record.dateEpochDay),
        subtitle = listOf(record.performedBy, record.remarks)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        trailing = {
            StatusChip(
                text = stringResource(
                    if (record.launched) R.string.sc_drill_launched else R.string.sc_drill_not_launched,
                ),
                color = if (record.launched) ConditionColors.Good else ConditionColors.NotChecked,
            )
        },
    )
    HorizontalDivider()
}

@Composable
internal fun DrillForm(
    draft: DrillDraft,
    onChange: ((DrillDraft) -> DrillDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(Dimens.SpacingL)) {
        Column(
            modifier = Modifier.padding(Dimens.SpacingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            Text(
                text = stringResource(R.string.sc_drill_record),
                style = MaterialTheme.typography.titleMedium,
            )
            DateField(
                label = stringResource(R.string.sc_drill_date),
                epochDay = draft.dateEpochDay,
                onChange = { day -> onChange { it.copy(dateEpochDay = day) } },
                labels = dateFieldLabels(),
                required = true,
            )
            OutlinedTextField(
                value = draft.performedBy,
                onValueChange = { value -> onChange { it.copy(performedBy = value) } },
                label = { Text(stringResource(R.string.sc_drill_performed_by) + " *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sc_drill_launched_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = draft.launched,
                    onCheckedChange = { value -> onChange { it.copy(launched = value) } },
                )
            }
            OutlinedTextField(
                value = draft.remarks,
                onValueChange = { value -> onChange { it.copy(remarks = value) } },
                label = { Text(stringResource(R.string.sc_drill_remarks)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
            FormButtons(
                saveLabel = stringResource(R.string.sc_save),
                saveEnabled = draft.isValid,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }
}

// ---------------------------------------------------------------------------- shared bits

@Composable
private fun FormButtons(
    saveLabel: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
        ) { Text(stringResource(R.string.sc_cancel)) }
        androidx.compose.material3.Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Text(text = saveLabel, modifier = Modifier.padding(start = Dimens.SpacingS))
        }
    }
}

@Composable
internal fun dateFieldLabels(): DateFieldLabels = DateFieldLabels(
    pick = stringResource(R.string.sc_date_pick),
    clear = stringResource(R.string.sc_date_clear),
    confirm = stringResource(R.string.sc_ok),
    cancel = stringResource(R.string.sc_cancel),
)
