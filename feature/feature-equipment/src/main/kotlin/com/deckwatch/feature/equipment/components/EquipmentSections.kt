@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deckwatch.feature.equipment.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DueDeltaChip
import com.deckwatch.core.designsystem.components.RegulationCardView
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.SeverityChip
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.components.TaskStatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.Severity
import com.deckwatch.feature.equipment.ChecklistItemUi
import com.deckwatch.feature.equipment.ConditionUndo
import com.deckwatch.feature.equipment.DeficiencyDraft
import com.deckwatch.feature.equipment.R
import com.deckwatch.feature.equipment.TaskRowUi
import com.deckwatch.feature.equipment.attributes.AttributeDraft
import com.deckwatch.feature.equipment.attributes.AttributeError
import com.deckwatch.feature.equipment.attributes.AttributeForm
import com.deckwatch.feature.equipment.attributes.AttributeSummaryList
import com.deckwatch.feature.equipment.conditionLabel
import com.deckwatch.feature.equipment.dueDeltaText
import com.deckwatch.feature.equipment.formatDate
import com.deckwatch.feature.equipment.localised
import com.deckwatch.feature.equipment.photo.PhotoStore
import com.deckwatch.feature.equipment.regulationCardLabels
import com.deckwatch.feature.equipment.severityLabel
import com.deckwatch.feature.equipment.taskStatusLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sections of the equipment sheet and the full-screen detail.
 *
 * Every heading is the design system's [SectionHeader], every row its [DeckWatchListRow], every
 * status its `StatusChip` and every grade its `ConditionChipRow` — this module defines no chrome of
 * its own (DESIGN_OVERHAUL, definition of done). The shared components carry their own horizontal
 * gutter, so the screens that host these sections leave their scroll column unpadded and each block
 * below pads only the content the design system does not.
 */

/** The gutter the shared list row and section header apply; module-local blocks match it. */
private val Gutter = Dimens.SpacingL

/**
 * A one-line "nothing here yet" note inside a section that is one of several on a scrolling screen.
 *
 * Deliberately *not* `EmptyState`: that is the whole-screen teaching state of rule 7 and measures
 * itself with `fillMaxSize`, which cannot live inside a vertically scrolling column. Screen-level
 * empties (the catalogue with no matches) do use `EmptyState`.
 */
@Composable
internal fun SectionEmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = Dimens.SpacingXs),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .padding(horizontal = Gutter, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/**
 * The identity block at the top of the peek stage — §7.4, in the order the overhaul fixes: tag
 * (monospace), type name, then the symbol tile.
 */
@Composable
internal fun EquipmentIdentity(
    symbolKey: String,
    tag: String,
    typeName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .padding(horizontal = Gutter, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        SymbolTile(
            symbolKey = symbolKey,
            size = IdentityTileSize,
            contentDescription = null,
            modifier = Modifier.padding(start = Dimens.SpacingM),
        )
    }
}

private val IdentityTileSize = 48.dp

/** Next due date with the shared day-delta chip — §7.4 peek, DESIGN_OVERHAUL rule 6. */
@Composable
internal fun NextDueRow(
    dueDate: Long?,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
    taskTitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .padding(horizontal = Gutter, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.equip_next_due),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatDate(dueDate), style = MaterialTheme.typography.bodyMedium)
            taskTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (dueDate == null) {
            StatusChip(
                text = stringResource(R.string.equip_next_due_none),
                color = ConditionColors.NotChecked,
            )
        } else {
            DueDeltaChip(
                daysUntilDue = dueDate - todayEpochDay,
                text = dueDeltaText(dueDate, todayEpochDay),
            )
        }
    }
}

/**
 * The inline confirmation of a grade — "Graded Good · Undo" — with the ten-second affordance of
 * DESIGN_OVERHAUL rules 8 and 10.
 *
 * It is a snackbar in everything but plumbing: it sits inside the sheet the officer is already
 * looking at, so a sweep never has to chase a floating host snackbar. The window itself is the view
 * model's; this only renders it.
 */
@Composable
internal fun ConditionUndoBar(
    undo: ConditionUndo,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = Dimens.SpacingXs),
        shape = RoundedCornerShape(Dimens.ChipCorner),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetMin)
                .padding(start = Dimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.equip_condition_graded, conditionLabel(undo.newGrade)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onUndo,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) { Text(stringResource(R.string.equip_condition_undo)) }
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
        SectionHeader(
            text = stringResource(R.string.equip_monthly_checklist),
            trailing = {
                StatusChip(
                    text = stringResource(
                        R.string.equip_monthly_progress,
                        items.count { it.checked },
                        items.size,
                    ),
                    color = if (complete) ConditionColors.Good else ConditionColors.NotChecked,
                )
            },
        )
        items.forEach { item ->
            DeckWatchListRow(
                title = localised(item.labelEn, item.labelTr),
                onClick = { onToggle(item.key, !item.checked) },
                leading = {
                    Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.key, it) })
                },
            )
        }
        if (complete) {
            OutlinedButton(
                onClick = onLog,
                enabled = canLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = Dimens.SpacingS)
                    .heightIn(min = Dimens.TouchTargetPrimary),
            ) { Text(stringResource(R.string.equip_monthly_log)) }
            if (!canLog) SectionEmptyLine(stringResource(R.string.equip_monthly_no_task))
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = Dimens.SpacingS),
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
                label = { Text(stringResource(R.string.equip_deficiency_field_title_required)) },
                singleLine = true,
                isError = draft.title.isBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpacingS)
                    .heightIn(min = Dimens.TouchTargetPrimary),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.equip_deficiency_field_description)) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpacingS)
                    .heightIn(min = Dimens.TouchTargetPrimary),
            )
            OutlinedTextField(
                value = draft.raisedBy,
                onValueChange = onRaisedByChange,
                label = { Text(stringResource(R.string.equip_deficiency_field_raised_by)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpacingS)
                    .heightIn(min = Dimens.TouchTargetPrimary),
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
            SectionEmptyLine(stringResource(R.string.equip_open_deficiencies_none))
            return@Column
        }
        deficiencies.forEach { deficiency ->
            DeckWatchListRow(
                title = deficiency.title,
                subtitle = formatDate(deficiency.raisedDate),
                trailing = {
                    SeverityChip(
                        severity = deficiency.severity,
                        text = severityLabel(deficiency.severity),
                    )
                },
            )
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
            SectionEmptyLine(stringResource(R.string.equip_tasks_none))
            return@Column
        }
        tasks.forEach { task ->
            val date = formatDate(task.completedDate ?: task.dueDate)
            val delta = if (task.completedDate == null) dueDeltaText(task.dueDate, todayEpochDay) else null
            DeckWatchListRow(
                title = localised(task.titleEn, task.titleTr),
                subtitle = listOfNotNull(date, delta, performedByLabel(task)).joinToString(" · "),
                trailing = { TaskStatusChip(status = task.status, text = taskStatusLabel(task.status)) },
            )
        }
    }
}

/** `SHIP_STAFF` -> `ship staff`; the register's own vocabulary, shown verbatim (see `statusLabel`). */
private fun performedByLabel(task: TaskRowUi): String =
    task.performedBy.name.lowercase().replace('_', ' ')

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
            SectionEmptyLine(stringResource(R.string.equip_requirements_none))
            return@Column
        }
        cards.forEach { card ->
            DeckWatchListRow(
                title = card.citation,
                titleIsTag = true,
                subtitle = card.title,
                onClick = { onOpen(card) },
            )
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
        if (editorValues == null) {
            SectionHeader(stringResource(R.string.equip_attributes))
            AttributeSummaryList(
                schema = schema,
                values = values,
                modifier = Modifier.padding(horizontal = Gutter),
            )
            if (schema.isNotEmpty()) {
                OutlinedButton(
                    onClick = onStartEditing,
                    modifier = Modifier
                        .padding(horizontal = Gutter, vertical = Dimens.SpacingS)
                        .heightIn(min = Dimens.TouchTargetPrimary),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = Dimens.SpacingS),
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
 * Photos taken against this item — §7.6.
 *
 * Thumbnails are decoded off the main thread and downsampled by [PhotoStore]; a full-resolution
 * capture would be tens of megabytes decoded, and a sheet holding several of them would not
 * survive on a mid-range phone. A URI whose file is gone (restored backup, cleared storage)
 * renders as the placeholder row rather than a broken frame, and can still be removed.
 */
@Composable
internal fun PhotoSection(
    photoUris: List<String>,
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.equip_photos))
        if (photoUris.isEmpty()) {
            SectionEmptyLine(stringResource(R.string.equip_photos_none))
            return@Column
        }
        photoUris.forEachIndexed { index, uri ->
            PhotoRow(uri = uri, index = index, onRemove = { onRemove(uri) })
        }
    }
}

@Composable
private fun PhotoRow(uri: String, index: Int, onRemove: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            PhotoStore.decodeThumbnail(context, uri, PhotoStore.THUMBNAIL_MAX_EDGE)?.asImageBitmap()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val image = thumbnail
        if (image == null) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(PhotoThumbnailSize),
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.equip_photo_placeholder, index + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(PhotoThumbnailSize)
                    .clip(RoundedCornerShape(Dimens.SpacingXs)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.SpacingS),
        ) {
            Text(
                text = stringResource(R.string.equip_photo_placeholder, index + 1),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (image == null) stringResource(R.string.equip_photo_missing) else uri,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.equip_photo_remove, index + 1),
            )
        }
    }
}

private val PhotoThumbnailSize = 56.dp
