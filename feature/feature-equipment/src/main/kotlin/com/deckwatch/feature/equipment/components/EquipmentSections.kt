@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deckwatch.feature.equipment.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.deckwatch.core.designsystem.components.RegulationCardView
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.Severity
import com.deckwatch.feature.equipment.ChecklistItemUi
import com.deckwatch.feature.equipment.DeficiencyDraft
import com.deckwatch.feature.equipment.R
import com.deckwatch.feature.equipment.TaskRowUi
import com.deckwatch.feature.equipment.attributes.AttributeDraft
import com.deckwatch.feature.equipment.attributes.AttributeError
import com.deckwatch.feature.equipment.attributes.AttributeForm
import com.deckwatch.feature.equipment.attributes.AttributeSummaryList
import com.deckwatch.feature.equipment.dueColor
import com.deckwatch.feature.equipment.dueDeltaText
import com.deckwatch.feature.equipment.formatDate
import com.deckwatch.feature.equipment.localised
import com.deckwatch.feature.equipment.regulationCardLabels
import com.deckwatch.feature.equipment.severityLabel
import com.deckwatch.feature.equipment.taskStatusLabel

/** A dense section heading — §14: no decoration that does not carry information. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = Dimens.SpacingM, bottom = Dimens.SpacingXs),
    )
}

/** One `label  value` row. Monospace values (tags, serials, certificate numbers) per §14. */
@Composable
internal fun LabelValue(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = Dimens.SpacingXs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LabelWidth),
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.attr_not_set),
            style = if (monospace) tagTextStyle() else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private val LabelWidth = 132.dp

/** Symbol, tag and type name — the identity block at the top of the peek stage (§7.4). */
@Composable
internal fun EquipmentIdentity(
    symbolKey: String,
    tag: String,
    typeName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SymbolTile(symbolKey = symbolKey, size = IdentityTileSize, contentDescription = null)
        Column(modifier = Modifier.padding(start = Dimens.SpacingM)) {
            Text(text = tag, style = tagTextStyle(), color = MaterialTheme.colorScheme.onSurface)
            Text(text = typeName, style = MaterialTheme.typography.titleMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val IdentityTileSize = 48.dp

/** Next due date with its colour-coded day delta — §7.4 peek. */
@Composable
internal fun DueLine(
    dueDate: Long?,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
    taskTitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.equip_next_due),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LabelWidth),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatDate(dueDate), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "  ${dueDeltaText(dueDate, todayEpochDay)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = dueColor(dueDate, todayEpochDay),
                )
            }
            taskTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The compact monthly checklist and its one-tap completion — §9.3. */
@Composable
internal fun MonthlyChecklistSection(
    items: List<ChecklistItemUi>,
    complete: Boolean,
    canLog: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_monthly_checklist))
        Text(
            text = stringResource(R.string.equip_monthly_progress, items.count { it.checked }, items.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTargetMin)
                    .clickable { onToggle(item.key, !item.checked) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.key, it) })
                Text(
                    text = localised(item.labelEn, item.labelTr),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (complete) {
            OutlinedButton(
                onClick = onLog,
                enabled = canLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTargetPrimary)
                    .padding(top = Dimens.SpacingS),
            ) { Text(stringResource(R.string.equip_monthly_log)) }
            if (!canLog) {
                Text(
                    text = stringResource(R.string.equip_monthly_no_task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The inline raise-deficiency form of §7.3 — pre-filled, dismissible, never forced.
 */
@Composable
internal fun DeficiencyFormCard(
    draft: DeficiencyDraft,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRaisedByChange: (String) -> Unit,
    onSeverityChange: (Severity) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
        shape = RoundedCornerShape(Dimens.CardCorner),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Text(
                text = stringResource(R.string.equip_deficiency_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.equip_deficiency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.equip_deficiency_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.equip_deficiency_field_description)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
            )
            OutlinedTextField(
                value = draft.raisedBy,
                onValueChange = onRaisedByChange,
                label = { Text(stringResource(R.string.equip_deficiency_field_raised_by)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
            )
            Text(
                text = stringResource(R.string.equip_deficiency_field_severity),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
                Severity.entries.forEach { severity ->
                    FilterChip(
                        selected = severity == draft.severity,
                        onClick = { onSeverityChange(severity) },
                        label = { Text(severityLabel(severity)) },
                        modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                ) { Text(stringResource(R.string.equip_deficiency_dismiss)) }
                Button(
                    onClick = onSave,
                    enabled = draft.title.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                ) { Text(stringResource(R.string.equip_deficiency_save)) }
            }
        }
    }
}

/** Open deficiencies on this item — §7.4 half stage. */
@Composable
internal fun DeficiencyList(deficiencies: List<Deficiency>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_open_deficiencies))
        if (deficiencies.isEmpty()) {
            Text(
                text = stringResource(R.string.equip_open_deficiencies_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        deficiencies.forEach { deficiency ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SpacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(SeverityDotSize)
                        .clip(RoundedCornerShape(SeverityDotSize / 2))
                        .background(ConditionColors.of(deficiency.severity)),
                )
                Column(modifier = Modifier.padding(start = Dimens.SpacingS).weight(1f)) {
                    Text(text = deficiency.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${severityLabel(deficiency.severity)} · ${formatDate(deficiency.raisedDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The full task list with per-task due dates and status — §7.4 full stage. */
@Composable
internal fun TaskListSection(
    tasks: List<TaskRowUi>,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_tasks))
        if (tasks.isEmpty()) {
            Text(
                text = stringResource(R.string.equip_tasks_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        tasks.forEach { task ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SpacingXs)) {
                Text(text = localised(task.titleEn, task.titleTr), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDate(task.completedDate ?: task.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "  ${taskStatusLabel(task.status)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ConditionColors.of(task.status),
                    )
                    Text(
                        text = "  ${task.performedBy.name.lowercase().replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

/** "Applicable requirements" — §8.4. Tapping a row opens the shared card in a dialog. */
@Composable
internal fun RequirementsSection(
    cards: List<RegulationCard>,
    onOpen: (RegulationCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_requirements))
        if (cards.isEmpty()) {
            Text(
                text = stringResource(R.string.equip_requirements_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        cards.forEach { card ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTargetMin)
                    .clickable { onOpen(card) }
                    .padding(vertical = Dimens.SpacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.citation,
                    style = tagTextStyle(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(LabelWidth),
                )
                Text(text = card.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One regulation card, opened without leaving the equipment — §8.4. */
@Composable
internal fun RegulationCardDialog(card: RegulationCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Dimens.CardCorner), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(Dimens.SpacingS).verticalScroll(rememberScrollState())) {
                RegulationCardView(card = card, labels = regulationCardLabels())
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).heightIn(min = Dimens.TouchTargetMin),
                ) { Text(stringResource(R.string.equip_requirements_close)) }
            }
        }
    }
}

/**
 * The dynamic attributes block: read-only summary with an edit entry, swapping to the shared
 * [AttributeForm] while editing — §7.4 full stage, §9.3.
 */
@Composable
internal fun AttributesSection(
    schema: List<AttributeDefinition>,
    values: AttributeDraft,
    editorValues: AttributeDraft?,
    errors: Map<String, AttributeError>,
    onStartEditing: () -> Unit,
    onValueChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_attributes))
        if (editorValues == null) {
            AttributeSummaryList(schema = schema, values = values)
            if (schema.isNotEmpty()) {
                OutlinedButton(
                    onClick = onStartEditing,
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary).padding(top = Dimens.SpacingS),
                ) { Text(stringResource(R.string.equip_attributes_edit)) }
            }
        } else {
            AttributeForm(
                schema = schema,
                values = editorValues,
                errors = errors,
                onValueChange = onValueChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                ) { Text(stringResource(R.string.equip_attributes_cancel)) }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
                ) { Text(stringResource(R.string.equip_attributes_save)) }
            }
        }
    }
}

/**
 * Photo list — URIs only. Rendering the images themselves waits for the photo phase, so each entry
 * is a placeholder row naming the file rather than a broken thumbnail.
 */
@Composable
internal fun PhotoSection(photoUris: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_photos))
        if (photoUris.isEmpty()) {
            Text(
                text = stringResource(R.string.equip_photos_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        photoUris.forEachIndexed { index, uri ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Outlined.Image, contentDescription = null)
                Column(modifier = Modifier.padding(start = Dimens.SpacingS)) {
                    Text(
                        text = stringResource(R.string.equip_photo_placeholder, index + 1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = uri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val SeverityDotSize = 10.dp
