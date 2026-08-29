@file:OptIn(ExperimentalMaterial3Api::class)

package com.deckwatch.feature.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.feature.equipment.attributes.AttributeForm
import com.deckwatch.feature.equipment.components.SectionHeader

/**
 * The add-equipment flow as a full-height sheet — §7.5.
 *
 * Three steps in one sheet:
 * 1. **Catalogue** — a search field over the whole catalogue plus two tabs: *By category*, with a
 *    collapsible section per [com.deckwatch.core.model.EquipmentGroup], and *Recent*, which is
 *    in-memory view-model state for this session. Every row carries its IMO symbol, the English and
 *    Turkish names and one line of help.
 * 2. **Details** — pre-populated: symbol from the type, tag auto-suggested as `PREFIX-DECK-NN`
 *    ([TagSuggestion]), position and deck from where the officer long-pressed. Only the tag is
 *    required.
 * 3. **Attributes** — the type's dynamic schema (§9.3), with the live due-date preview of §7.5.4
 *    under every field that can move a date.
 *
 * A *Copies* stepper creates N items with incremented tags, laid out in a small grid around the drop
 * point for the officer to drag apart — the fourteen-extinguishers case of §7.5.
 *
 * @param onCreated the ids written, in creation order; the sheet dismisses itself straight after.
 */
@Composable
fun AddEquipmentSheet(
    vesselId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    deckId: String? = null,
    zoneId: String? = null,
    posX: Float = 0.5f,
    posY: Float = 0.5f,
    onCreated: (ids: List<String>) -> Unit = {},
) {
    val viewModel: AddEquipmentViewModel = hiltViewModel()
    LaunchedEffect(vesselId, deckId, zoneId, posX, posY) {
        viewModel.bind(vesselId, deckId, zoneId, posX, posY)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.createdIds) {
        if (state.createdIds.isNotEmpty()) {
            onCreated(state.createdIds)
            viewModel.consumeCreated()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = Dimens.SpacingL)
                .navigationBarsPadding(),
        ) {
            AddHeader(step = state.step, onClose = onDismiss)
            HorizontalDivider(modifier = Modifier.padding(bottom = Dimens.SpacingS))

            when (state.step) {
                AddStep.CATALOGUE -> CatalogueStep(
                    state = state,
                    onQuery = viewModel::setQuery,
                    onTab = viewModel::setTab,
                    onToggleGroup = viewModel::toggleGroup,
                    onSelect = viewModel::selectType,
                    modifier = Modifier.weight(1f),
                )
                AddStep.DETAILS -> DetailsStep(
                    state = state,
                    onForm = viewModel::updateForm,
                    onCopies = viewModel::setCopies,
                    modifier = Modifier.weight(1f),
                )
                AddStep.ATTRIBUTES -> AttributesStep(
                    state = state,
                    onAttribute = viewModel::setAttribute,
                    modifier = Modifier.weight(1f),
                )
            }

            StepButtons(
                state = state,
                onBack = {
                    when (state.step) {
                        AddStep.CATALOGUE -> onDismiss()
                        AddStep.DETAILS -> viewModel.backToCatalogue()
                        AddStep.ATTRIBUTES -> viewModel.goTo(AddStep.DETAILS)
                    }
                },
                onNext = { viewModel.goTo(AddStep.ATTRIBUTES) },
                onCreate = viewModel::create,
            )
        }
    }
}

@Composable
private fun AddHeader(step: AddStep, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.add_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(
                    when (step) {
                        AddStep.CATALOGUE -> R.string.add_step_catalogue
                        AddStep.DETAILS -> R.string.add_step_details
                        AddStep.ATTRIBUTES -> R.string.add_step_attributes
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.equip_close))
        }
    }
}

// ---------------------------------------------------------------------- step 1

@Composable
private fun CatalogueStep(
    state: AddEquipmentUiState,
    onQuery: (String) -> Unit,
    onTab: (CatalogueTab) -> Unit,
    onToggleGroup: (EquipmentGroup) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.add_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin),
        )
        TabRow(selectedTabIndex = if (state.tab == CatalogueTab.BY_CATEGORY) 0 else 1) {
            Tab(
                selected = state.tab == CatalogueTab.BY_CATEGORY,
                onClick = { onTab(CatalogueTab.BY_CATEGORY) },
                text = { Text(stringResource(R.string.add_tab_category)) },
            )
            Tab(
                selected = state.tab == CatalogueTab.RECENT,
                onClick = { onTab(CatalogueTab.RECENT) },
                text = { Text(stringResource(R.string.add_tab_recent)) },
            )
        }

        if (state.tab == CatalogueTab.RECENT) {
            if (state.recent.isEmpty()) {
                EmptyLine(stringResource(R.string.add_recent_empty))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.recent, key = { it.typeKey }) { entry ->
                        CatalogueRow(entry = entry, onClick = { onSelect(entry.typeKey) })
                    }
                }
            }
            return@Column
        }

        if (state.groups.isEmpty()) {
            EmptyLine(stringResource(R.string.add_no_results))
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            state.groups.forEach { group ->
                val expanded = group.group in state.expandedGroups
                item(key = "group-${group.group.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.TouchTargetMin)
                            .clickable { onToggleGroup(group.group) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = groupLabel(group.group),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.add_group_count, group.entries.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (expanded) {
                    items(group.entries, key = { it.typeKey }) { entry ->
                        CatalogueRow(entry = entry, onClick = { onSelect(entry.typeKey) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueRow(entry: CatalogueEntryUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowComfortable)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolTile(symbolKey = entry.symbolKey, contentDescription = null)
        Column(modifier = Modifier.padding(start = Dimens.SpacingM).weight(1f)) {
            Text(text = entry.nameEn, style = MaterialTheme.typography.bodyLarge)
            if (entry.nameTr.isNotBlank()) {
                Text(
                    text = entry.nameTr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val help = localised(entry.helpEn, entry.helpTr)
            if (help.isNotBlank()) {
                Text(
                    text = help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- step 2

@Composable
private fun DetailsStep(
    state: AddEquipmentUiState,
    onForm: ((EquipmentFormState) -> EquipmentFormState) -> Unit,
    onCopies: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = state.selectedType ?: return
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolTile(symbolKey = type.symbolKey, contentDescription = null)
            Text(
                text = localised(type.nameEn, type.nameTr),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Dimens.SpacingM),
            )
        }

        OutlinedTextField(
            value = state.form.tag,
            onValueChange = { value -> onForm { it.copy(tag = value) } },
            label = { Text(stringResource(R.string.add_field_tag_required)) },
            isError = state.tagError,
            singleLine = true,
            textStyle = tagTextStyle(),
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingM),
        )
        if (state.tagError) {
            Text(
                text = stringResource(R.string.add_tag_required_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        FormField(stringResource(R.string.add_field_name), state.form.name) { value -> onForm { it.copy(name = value) } }
        FormField(stringResource(R.string.add_field_location), state.form.location) { value ->
            onForm { it.copy(location = value) }
        }
        FormField(stringResource(R.string.add_field_maker), state.form.maker) { value -> onForm { it.copy(maker = value) } }
        FormField(stringResource(R.string.add_field_model), state.form.model) { value -> onForm { it.copy(model = value) } }
        FormField(stringResource(R.string.add_field_serial), state.form.serial) { value ->
            onForm { it.copy(serial = value) }
        }
        FormField(stringResource(R.string.add_field_type_approval), state.form.typeApproval) { value ->
            onForm { it.copy(typeApproval = value) }
        }
        FormField(
            label = stringResource(R.string.add_field_quantity),
            value = state.form.quantity,
            keyboardType = KeyboardType.Number,
        ) { value -> onForm { it.copy(quantity = value) } }
        EpochDayField(stringResource(R.string.add_field_manufacture_date), state.form.manufactureDate) { value ->
            onForm { it.copy(manufactureDate = value) }
        }
        EpochDayField(stringResource(R.string.add_field_installed_date), state.form.installedDate) { value ->
            onForm { it.copy(installedDate = value) }
        }
        FormField(stringResource(R.string.add_field_notes), state.form.notes) { value -> onForm { it.copy(notes = value) } }

        SectionHeader(stringResource(R.string.add_copies))
        Text(
            text = stringResource(R.string.add_copies_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            OutlinedButton(
                onClick = { onCopies(state.copies - 1) },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary),
            ) { Text("−") }
            Text(text = state.copies.toString(), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { onCopies(state.copies + 1) },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetPrimary),
            ) { Text("+") }
        }

        DuePreviewPanel(state)
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpacingS)
            .heightIn(min = Dimens.TouchTargetMin),
    )
}

/**
 * A date on the fixed form. It reuses the schema form's `DATE` field by wrapping the value in a
 * one-off [AttributeDefinition], so there is exactly one date picker implementation in the module.
 */
@Composable
private fun EpochDayField(label: String, epochDay: Long?, onValueChange: (Long?) -> Unit) {
    AttributeForm(
        schema = listOf(AttributeDefinition(key = label, kind = AttributeKind.DATE, labelEn = label, labelTr = label)),
        values = mapOf(label to (epochDay?.toString().orEmpty())),
        errors = emptyMap(),
        onValueChange = { _, raw -> onValueChange(raw.toLongOrNull()) },
        modifier = Modifier.padding(top = Dimens.SpacingS),
    )
}

// ---------------------------------------------------------------------- step 3

@Composable
private fun AttributesStep(
    state: AddEquipmentUiState,
    onAttribute: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = state.selectedType ?: return
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        AttributeForm(
            schema = type.attributeSchema,
            values = state.attributes,
            errors = state.attributeErrors,
            onValueChange = onAttribute,
            footerFor = { definition -> AttributeDueFooter(state, definition) },
        )
        DuePreviewPanel(state)
    }
}

/**
 * §7.5.4 — the live per-field consequence.
 *
 * An `affectsTasks` value names the tasks it adds; a `DATE` value shows the due date that follows if
 * it is the last performance of the task it names ([AttributeTaskLink]). A date the model cannot
 * link to a task shows nothing rather than a guessed interval (§8.5).
 */
@Composable
private fun AttributeDueFooter(state: AddEquipmentUiState, definition: AttributeDefinition) {
    val type = state.selectedType ?: return
    val raw = state.attributes[definition.key].orEmpty()

    val added = DuePreview.tasksAddedBy(type, definition, raw)
        .mapNotNull { key -> state.definitions[key] }
        .map { localised(it.titleEn, it.titleTr) }
    if (added.isNotEmpty()) {
        Text(
            text = stringResource(R.string.add_task_added, added.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = ConditionColors.Good,
        )
    }

    val anchor = DuePreview.anchorPreview(
        attribute = definition,
        enteredEpochDay = raw.trim().toLongOrNull(),
        derivedTaskKeys = state.duePreview.map { it.taskKey },
        definitions = state.definitions,
        vessel = state.vesselContext,
        todayEpochDay = state.todayEpochDay,
    )
    if (anchor?.dueDate != null) {
        Text(
            text = stringResource(
                R.string.add_due_anchor,
                localised(anchor.titleEn, anchor.titleTr),
                formatDate(anchor.dueDate),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = dueColor(anchor.dueDate, state.todayEpochDay),
        )
    }
}

/** The whole schedule the item will get on save — §7.5.4. */
@Composable
private fun DuePreviewPanel(state: AddEquipmentUiState) {
    SectionHeader(stringResource(R.string.add_due_preview))
    if (state.duePreview.isEmpty()) {
        EmptyLine(stringResource(R.string.add_due_preview_none))
        return
    }
    state.duePreview.forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SpacingXs)) {
            Text(
                text = localised(row.titleEn, row.titleTr),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.dueDate?.let { stringResource(R.string.add_due_preview_row, formatDate(it)) }.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = dueColor(row.dueDate, state.todayEpochDay),
            )
        }
    }
}

// ---------------------------------------------------------------------- footer

@Composable
private fun StepButtons(
    state: AddEquipmentUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Text(stringResource(if (state.step == AddStep.CATALOGUE) R.string.equip_cancel else R.string.add_back))
        }
        when (state.step) {
            AddStep.CATALOGUE -> Unit
            AddStep.DETAILS -> Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
            ) { Text(stringResource(R.string.add_next)) }
            AddStep.ATTRIBUTES -> Button(
                onClick = onCreate,
                enabled = !state.saving,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTargetPrimary),
            ) {
                Text(
                    if (state.copies > 1) {
                        stringResource(R.string.add_create_n, state.copies)
                    } else {
                        stringResource(R.string.add_create)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Dimens.SpacingM),
    )
}
