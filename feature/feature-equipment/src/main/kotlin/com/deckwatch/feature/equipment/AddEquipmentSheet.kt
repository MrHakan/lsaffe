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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.DateField
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SearchField
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.feature.equipment.attributes.AttributeFieldGroup
import com.deckwatch.feature.equipment.attributes.AttributeGroup
import com.deckwatch.feature.equipment.attributes.attributeGroupTitle
import com.deckwatch.feature.equipment.attributes.attributesIn
import com.deckwatch.feature.equipment.attributes.requiredLabel

/**
 * The add-equipment flow as a full-height sheet — §7.5.
 *
 * Two steps, one primary action each (DESIGN_OVERHAUL rule 1):
 * 1. **Catalogue** — the shared `SearchField`, keyboard-ready and auto-focused (rule 9), over the
 *    whole catalogue; then two tabs, *By category* with a collapsible section per
 *    [com.deckwatch.core.model.EquipmentGroup] carrying its count, and *Recent*, which is in-memory
 *    view-model state for this session. Every row is a `DeckWatchListRow` with the type's IMO symbol.
 * 2. **The record** — one scrolling form, pre-populated: symbol from the type, tag auto-suggested as
 *    `PREFIX-DECK-NN` ([TagSuggestion]) and shown in monospace, position and deck from where the
 *    officer long-pressed. Required fields carry a `*`; the record's own fields and the type's
 *    dynamic schema (§9.3) are interleaved under one set of headings — identification, dates,
 *    checks — and every date is the shared `DateField`. The due dates a date will generate appear
 *    live beneath it as status chips (§7.5.4). *Save* is a full-width 56dp bottom bar, enabled only
 *    when the form would save cleanly.
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
                .navigationBarsPadding(),
        ) {
            DeckWatchTopBar(
                title = stringResource(R.string.add_title),
                subtitle = stringResource(
                    when (state.step) {
                        AddStep.CATALOGUE -> R.string.add_step_catalogue
                        AddStep.DETAILS -> R.string.add_step_details
                    },
                ),
                onBack = {
                    when (state.step) {
                        AddStep.CATALOGUE -> onDismiss()
                        AddStep.DETAILS -> viewModel.backToCatalogue()
                    }
                },
                backContentDescription = stringResource(
                    if (state.step == AddStep.CATALOGUE) R.string.equip_close else R.string.add_back,
                ),
            )
            HorizontalDivider()

            when (state.step) {
                AddStep.CATALOGUE -> CatalogueStep(
                    state = state,
                    onQuery = viewModel::setQuery,
                    onTab = viewModel::setTab,
                    onToggleGroup = viewModel::toggleGroup,
                    onSelect = viewModel::selectType,
                    modifier = Modifier.weight(1f),
                )

                AddStep.DETAILS -> {
                    RecordStep(
                        state = state,
                        onForm = viewModel::updateForm,
                        onCopies = viewModel::setCopies,
                        onAttribute = viewModel::setAttribute,
                        modifier = Modifier.weight(1f),
                    )
                    SaveBar(state = state, onCreate = viewModel::create)
                }
            }
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
    // Rule 9: long lists open keyboard-ready. Search is the primary path through 150 catalogue types.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier = modifier.fillMaxWidth()) {
        SearchField(
            query = state.query,
            onQueryChange = onQuery,
            placeholder = stringResource(R.string.add_search),
            clearContentDescription = stringResource(R.string.add_search_clear),
            modifier = Modifier
                .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS)
                .focusRequester(focus),
        )
        TabRow(selectedTabIndex = if (state.tab == CatalogueTab.BY_CATEGORY) 0 else 1) {
            Tab(
                selected = state.tab == CatalogueTab.BY_CATEGORY,
                onClick = { onTab(CatalogueTab.BY_CATEGORY) },
                text = { Text(stringResource(R.string.add_tab_category)) },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
            Tab(
                selected = state.tab == CatalogueTab.RECENT,
                onClick = { onTab(CatalogueTab.RECENT) },
                text = { Text(stringResource(R.string.add_tab_recent)) },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
        }

        if (state.tab == CatalogueTab.RECENT) {
            if (state.recent.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.add_recent_empty_title),
                    body = stringResource(R.string.add_recent_empty),
                    actionLabel = stringResource(R.string.add_tab_category),
                    onAction = { onTab(CatalogueTab.BY_CATEGORY) },
                )
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
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.add_no_results_title),
                body = stringResource(R.string.add_no_results),
                actionLabel = stringResource(R.string.add_search_clear),
                onAction = { onQuery("") },
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            state.groups.forEach { group ->
                val expanded = group.group in state.expandedGroups
                item(key = "group-${group.group.name}") {
                    CatalogueGroupHeader(
                        group = group,
                        expanded = expanded,
                        onToggle = { onToggleGroup(group.group) },
                    )
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

/** A collapsible catalogue section — the shared [SectionHeader] with its count and a chevron. */
@Composable
private fun CatalogueGroupHeader(
    group: CatalogueGroupUi,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    SectionHeader(
        text = groupLabel(group.group),
        modifier = Modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .clickable(onClick = onToggle),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    text = stringResource(R.string.add_group_count, group.entries.size),
                    color = ConditionColors.NotChecked,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.add_group_collapse else R.string.add_group_expand,
                    ),
                    modifier = Modifier.padding(start = Dimens.SpacingS),
                )
            }
        },
    )
}

/** One catalogue entry — symbol, name and the one-line description of §7.5 step 2. */
@Composable
private fun CatalogueRow(entry: CatalogueEntryUi, onClick: () -> Unit) {
    DeckWatchListRow(
        title = localised(entry.nameEn, entry.nameTr),
        subtitle = localised(entry.helpEn, entry.helpTr),
        onClick = onClick,
        leading = { SymbolTile(symbolKey = entry.symbolKey, contentDescription = null) },
    )
}

// ---------------------------------------------------------------------- step 2

@Composable
private fun RecordStep(
    state: AddEquipmentUiState,
    onForm: ((EquipmentFormState) -> EquipmentFormState) -> Unit,
    onCopies: (Int) -> Unit,
    onAttribute: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = state.selectedType ?: return
    val schema = type.attributeSchema
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetPrimary)
                .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolTile(symbolKey = type.symbolKey, contentDescription = null)
            Text(
                text = localised(type.nameEn, type.nameTr),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Dimens.SpacingM),
            )
        }

        // ---- identification
        SectionHeader(stringResource(attributeGroupTitle(AttributeGroup.IDENTIFICATION)))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpacingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            OutlinedTextField(
                value = state.form.tag,
                onValueChange = { value -> onForm { it.copy(tag = value) } },
                label = { Text(requiredLabel(stringResource(R.string.add_field_tag), required = true)) },
                isError = state.tagError || state.form.tag.isBlank(),
                supportingText = if (state.form.tag.isBlank()) {
                    { Text(stringResource(R.string.add_tag_required_error)) }
                } else {
                    null
                },
                singleLine = true,
                textStyle = tagTextStyle(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetPrimary),
            )
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
            FormField(stringResource(R.string.add_field_notes), state.form.notes) { value ->
                onForm { it.copy(notes = value) }
            }
        }
        AttributeFieldGroup(
            fields = attributesIn(schema, AttributeGroup.IDENTIFICATION),
            values = state.attributes,
            errors = state.attributeErrors,
            onValueChange = onAttribute,
            modifier = Modifier.padding(top = Dimens.SpacingM),
            footerFor = { definition -> AttributeDueFooter(state, definition) },
        )

        // ---- dates, each with the schedule it will generate
        SectionHeader(stringResource(attributeGroupTitle(AttributeGroup.DATES)))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpacingL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            DateField(
                label = stringResource(R.string.add_field_manufacture_date),
                epochDay = state.form.manufactureDate,
                onChange = { value -> onForm { it.copy(manufactureDate = value) } },
                labels = dateFieldLabels(),
            )
            DateField(
                label = stringResource(R.string.add_field_installed_date),
                epochDay = state.form.installedDate,
                onChange = { value -> onForm { it.copy(installedDate = value) } },
                labels = dateFieldLabels(),
            )
            // §7.5.4: the two anchor dates above drive the whole schedule, so it reads directly
            // under them rather than at the far end of the form.
            DuePreviewLines(state)
        }
        AttributeFieldGroup(
            fields = attributesIn(schema, AttributeGroup.DATES),
            values = state.attributes,
            errors = state.attributeErrors,
            onValueChange = onAttribute,
            modifier = Modifier.padding(top = Dimens.SpacingM),
            footerFor = { definition -> AttributeDueFooter(state, definition) },
        )

        // ---- checks
        val checks = attributesIn(schema, AttributeGroup.CHECKS)
        if (checks.isNotEmpty()) {
            SectionHeader(stringResource(attributeGroupTitle(AttributeGroup.CHECKS)))
            AttributeFieldGroup(
                fields = checks,
                values = state.attributes,
                errors = state.attributeErrors,
                onValueChange = onAttribute,
            )
        }

        // ---- duplicate ×N
        SectionHeader(stringResource(R.string.add_copies))
        CopiesStepper(copies = state.copies, onCopies = onCopies)
        Column(modifier = Modifier.padding(bottom = Dimens.SpacingXl)) {}
    }
}

@Composable
private fun CopiesStepper(copies: Int, onCopies: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpacingL)) {
        Text(
            text = stringResource(R.string.add_copies_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetPrimary)
                .padding(top = Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            IconButton(
                onClick = { onCopies(copies - 1) },
                modifier = Modifier.size(Dimens.TouchTargetMin),
            ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.add_decrease)) }
            Text(text = copies.toString(), style = MaterialTheme.typography.titleLarge)
            IconButton(
                onClick = { onCopies(copies + 1) },
                modifier = Modifier.size(Dimens.TouchTargetMin),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_increase)) }
        }
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
            .heightIn(min = Dimens.TouchTargetPrimary),
    )
}

/**
 * §7.5.4 — the live per-field consequence, as status chips.
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
        StatusChip(
            text = stringResource(R.string.add_task_added, added.joinToString(", ")),
            color = ConditionColors.Good,
            modifier = Modifier.padding(top = Dimens.SpacingXs),
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
        StatusChip(
            text = stringResource(
                R.string.add_due_anchor,
                localised(anchor.titleEn, anchor.titleTr),
                formatDate(anchor.dueDate),
            ),
            color = dueColor(anchor.dueDate, state.todayEpochDay),
            modifier = Modifier.padding(top = Dimens.SpacingXs),
        )
    }
}

/** The whole schedule the item will get on save — §7.5.4, one status chip per task. */
@Composable
private fun DuePreviewLines(state: AddEquipmentUiState) {
    if (state.duePreview.isEmpty()) {
        Text(
            text = stringResource(R.string.add_due_preview_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs)) {
        state.duePreview.forEach { row ->
            val due = row.dueDate ?: return@forEach
            StatusChip(
                text = stringResource(
                    R.string.add_due_anchor,
                    localised(row.titleEn, row.titleTr),
                    formatDate(due),
                ),
                color = dueColor(due, state.todayEpochDay),
            )
        }
    }
}

// ---------------------------------------------------------------------- the one primary action

/** Save — a full-width 56dp bottom bar, enabled only when the form would save cleanly (rule 1). */
@Composable
private fun SaveBar(state: AddEquipmentUiState, onCreate: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Button(
            onClick = onCreate,
            enabled = state.canSave && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpacingL)
                .heightIn(min = Dimens.TouchTargetPrimary),
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
