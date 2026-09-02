package com.deckwatch.feature.inspection

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.DueDeltaChip
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.components.TaskStatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import kotlinx.coroutines.launch

/**
 * The Due work list — §12. A dense, swipeable list of what is owed, not a dashboard.
 *
 * The primary action is completing a job. It is reachable two ways, because a gesture nobody
 * discovers is not an action: swipe **right** marks done and swipe **left** defers, and tapping the
 * row opens the same three choices as a bottom sheet with 56dp targets (DESIGN_OVERHAUL rules 1 and
 * 3). Both gestures snap back and open a dialog rather than committing blind: this data ends up in a
 * survey file, so nothing destructive happens on a gesture alone (C10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueWorkListScreen(
    modifier: Modifier = Modifier,
    viewModel: DueViewModel = hiltViewModel(),
    onOpenEquipment: (String) -> Unit = {},
    onExportHtml: (DueExportRequest) -> Unit = {},
    onOpenRounds: () -> Unit = {},
    onOpenDeficiencies: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val turkish = isTurkishLocale()
    LaunchedEffect(turkish) { viewModel.setTurkish(turkish) }

    // ClipboardManager rather than the newer suspend Clipboard: the export is a synchronous string
    // build and this API is the one available on every device down to minSdk 26 (C4).
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.due_copied)
    val exportLabels = rememberExportLabels(labelOf(state.segment))

    var completing by remember { mutableStateOf<DueRow?>(null) }
    var deferring by remember { mutableStateOf<DueRow?>(null) }
    var acting by remember { mutableStateOf<DueRow?>(null) }
    var filterSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DueTopBar(
                state = state,
                onToggleSurveyPrep = viewModel::toggleSurveyPrep,
                onCopy = {
                    val text = renderDueListText(viewModel.buildExportRequest(), exportLabels)
                    clipboard.setText(AnnotatedString(text))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
                onExportHtml = { onExportHtml(viewModel.buildExportRequest()) },
                onOpenRounds = onOpenRounds,
                onOpenDeficiencies = onOpenDeficiencies,
            )
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

            SegmentChips(
                selected = state.segment,
                counts = state.counts,
                onSelect = viewModel::selectSegment,
            )
            if (!state.surveyPrepEnabled) {
                DueFilterBar(
                    state = state,
                    viewModel = viewModel,
                    onOpenSheet = { filterSheetOpen = true },
                )
            }
            HorizontalDivider()

            when {
                state.surveyPrepEnabled -> SurveyPrepContent(
                    state = state,
                    onLeave = viewModel::toggleSurveyPrep,
                    onOpenRow = { acting = it },
                )

                // Nothing anywhere and nothing filtered out: this vessel has no work on file yet.
                state.counts.values.sum() == 0 && !state.filters.isActive -> EmptyState(
                    icon = Icons.Filled.Checklist,
                    title = stringResource(R.string.due_empty_title),
                    body = stringResource(R.string.due_empty_hint),
                )

                else -> DueRowList(
                    rows = state.rows,
                    filtersActive = state.filters.isActive,
                    onClearFilters = viewModel::clearFilters,
                    onOpenRow = { acting = it },
                    onRequestComplete = { completing = it },
                    onRequestDefer = { deferring = it },
                )
            }
        }
    }

    if (filterSheetOpen) {
        DueFilterSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { filterSheetOpen = false },
        )
    }

    acting?.let { row ->
        DueRowActionSheet(
            row = row,
            onDismiss = { acting = null },
            onComplete = {
                acting = null
                completing = row
            },
            onDefer = {
                acting = null
                deferring = row
            },
            onOpenEquipment = {
                acting = null
                onOpenEquipment(row.equipmentId)
            },
        )
    }

    completing?.let { row ->
        CompletionDialog(
            row = row,
            defaultDate = state.today.takeIf { it != 0L } ?: Dates.todayEpochDay(),
            onDismiss = { completing = null },
            onConfirm = { input ->
                viewModel.completeTask(input)
                completing = null
            },
        )
    }

    deferring?.let { row ->
        DeferDialog(
            row = row,
            onDismiss = { deferring = null },
            onConfirm = { reason ->
                viewModel.deferTask(row.instanceId, reason)
                deferring = null
            },
        )
    }
}

@Composable
private fun DueTopBar(
    state: DueUiState,
    onToggleSurveyPrep: () -> Unit,
    onCopy: () -> Unit,
    onExportHtml: () -> Unit,
    onOpenRounds: () -> Unit,
    onOpenDeficiencies: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    DeckWatchTopBar(
        title = stringResource(R.string.due_title),
        subtitle = state.vesselName.takeIf { it.isNotEmpty() },
        actions = {
            IconButton(
                onClick = onToggleSurveyPrep,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                    contentDescription = stringResource(R.string.due_survey_prep),
                    tint = if (state.surveyPrepEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.due_copy_text),
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.due_more_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.due_export_html)) },
                        onClick = {
                            menuOpen = false
                            onExportHtml()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.due_open_rounds)) },
                        onClick = {
                            menuOpen = false
                            onOpenRounds()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.due_open_deficiencies)) },
                        onClick = {
                            menuOpen = false
                            onOpenDeficiencies()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun SegmentChips(
    selected: DueSegment,
    counts: Map<DueSegment, Int>,
    onSelect: (DueSegment) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        items(DueSegment.entries.toList(), key = { it.name }) { segment ->
            val count = counts[segment] ?: 0
            FilterChip(
                selected = segment == selected,
                onClick = { onSelect(segment) },
                label = { Text("${labelOf(segment)}  $count") },
                shape = RoundedCornerShape(Dimens.ChipCorner),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
        }
    }
}

/** One active filter, as the row shows it: a word and the tap that removes it. */
private data class ActiveFilter(val key: String, val label: String, val clear: () -> Unit)

@Composable
private fun activeFilters(state: DueUiState, viewModel: DueViewModel): List<ActiveFilter> {
    val filters = state.filters
    val options = state.options
    val result = mutableListOf<ActiveFilter>()
    options.decks.firstOrNull { it.id == filters.deckId }?.let {
        result += ActiveFilter("deck", it.label) { viewModel.setDeckFilter(null) }
    }
    options.zones.firstOrNull { it.id == filters.zoneId }?.let {
        result += ActiveFilter("zone", it.label) { viewModel.setZoneFilter(null) }
    }
    options.categories.firstOrNull { it.id == filters.categoryId }?.let {
        result += ActiveFilter("category", it.label) { viewModel.setCategoryFilter(null) }
    }
    filters.group?.let { group ->
        result += ActiveFilter("group", labelOf(group)) { viewModel.setGroupFilter(null) }
    }
    filters.performedBy?.let { performer ->
        result += ActiveFilter("performer", labelOf(performer)) { viewModel.setPerformedByFilter(null) }
    }
    filters.condition?.let { condition ->
        result += ActiveFilter("condition", labelOf(condition)) { viewModel.setConditionFilter(null) }
    }
    return result
}

/**
 * Six filter dimensions behind one chip — §12 kept, but folded away so the work list starts at the
 * top of the screen. What is actually filtering stays visible as removable chips beside it.
 */
@Composable
private fun DueFilterBar(state: DueUiState, viewModel: DueViewModel, onOpenSheet: () -> Unit) {
    val active = activeFilters(state, viewModel)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "filter") {
            FilterChip(
                selected = active.isNotEmpty(),
                onClick = onOpenSheet,
                leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = null) },
                label = {
                    Text(
                        if (active.isEmpty()) {
                            stringResource(R.string.due_filter)
                        } else {
                            stringResource(R.string.due_filter_badge, active.size)
                        },
                    )
                },
                shape = RoundedCornerShape(Dimens.ChipCorner),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
        }
        items(active, key = { it.key }) { filter ->
            InputChip(
                selected = true,
                onClick = filter.clear,
                label = { Text(filter.label) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.due_filter_remove, filter.label),
                    )
                },
                shape = RoundedCornerShape(Dimens.ChipCorner),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            )
        }
    }
}

/** The six dimensions of §12, one glove-sized row each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueFilterSheet(state: DueUiState, viewModel: DueViewModel, onDismiss: () -> Unit) {
    val options = state.options
    val filters = state.filters
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Dimens.SpacingL),
        ) {
            SectionHeader(text = stringResource(R.string.due_filter_sheet_title))
            FilterSheetRow(label = stringResource(R.string.due_filter_deck)) {
                FilterDropdownChip(
                    label = stringResource(R.string.due_filter_all),
                    selectedLabel = options.decks.firstOrNull { it.id == filters.deckId }?.label,
                    options = options.decks,
                    optionLabel = { it.label },
                    onSelect = { viewModel.setDeckFilter(it?.id) },
                )
            }
            FilterSheetRow(label = stringResource(R.string.due_filter_zone)) {
                FilterDropdownChip(
                    label = stringResource(R.string.due_filter_all),
                    selectedLabel = options.zones.firstOrNull { it.id == filters.zoneId }?.label,
                    options = options.zones,
                    optionLabel = { it.label },
                    onSelect = { viewModel.setZoneFilter(it?.id) },
                )
            }
            FilterSheetRow(label = stringResource(R.string.due_filter_category)) {
                FilterDropdownChip(
                    label = stringResource(R.string.due_filter_all),
                    selectedLabel = options.categories.firstOrNull { it.id == filters.categoryId }?.label,
                    options = options.categories,
                    optionLabel = { it.label },
                    onSelect = { viewModel.setCategoryFilter(it?.id) },
                )
            }
            FilterSheetRow(label = stringResource(R.string.due_filter_group)) {
                FilterDropdownChip(
                    label = stringResource(R.string.due_filter_all),
                    selectedLabel = filters.group?.let { labelOf(it) },
                    options = options.groups,
                    optionLabel = { labelOf(it) },
                    onSelect = { viewModel.setGroupFilter(it) },
                )
            }
            FilterSheetRow(label = stringResource(R.string.due_filter_performed_by)) {
                FilterDropdownChip(
                    label = stringResource(R.string.due_filter_all),
                    selectedLabel = filters.performedBy?.let { labelOf(it) },
                    options = options.performers,
                    optionLabel = { labelOf(it) },
                    onSelect = { viewModel.setPerformedByFilter(it) },
                )
            }
            FilterSheetRow(label = stringResource(R.string.due_filter_condition)) {
                FilterDropdownChip(
                    label = stringResource(R.string.due_filter_all),
                    selectedLabel = filters.condition?.let { labelOf(it) },
                    options = options.conditions,
                    optionLabel = { labelOf(it) },
                    onSelect = { viewModel.setConditionFilter(it) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::clearFilters,
                    enabled = filters.isActive,
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Text(stringResource(R.string.due_filter_clear))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.TouchTargetPrimary),
                ) {
                    Text(stringResource(R.string.due_filter_done))
                }
            }
        }
    }
}

@Composable
private fun FilterSheetRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

/** The row action menu — for everyone who never discovers the swipe (DESIGN_OVERHAUL rule 1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueRowActionSheet(
    row: DueRow,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onDefer: () -> Unit,
    onOpenEquipment: () -> Unit,
) {
    val turkish = isTurkishLocale()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            SectionHeader(text = stringResource(R.string.due_row_actions))
            Text(
                text = "${row.tag} · ${row.taskTitle.resolve(turkish)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingXs),
            )
            SheetActionRow(
                icon = Icons.Filled.TaskAlt,
                label = stringResource(R.string.due_swipe_done),
                onClick = onComplete,
            )
            SheetActionRow(
                icon = Icons.Filled.Schedule,
                label = stringResource(R.string.due_swipe_defer),
                onClick = onDefer,
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                label = stringResource(R.string.due_open_equipment),
                onClick = onOpenEquipment,
            )
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DeckWatchListRow(
        title = label,
        onClick = onClick,
        leading = { Icon(imageVector = icon, contentDescription = null) },
    )
}

@Composable
private fun DueRowList(
    rows: List<DueRow>,
    filtersActive: Boolean,
    onClearFilters: () -> Unit,
    onOpenRow: (DueRow) -> Unit,
    onRequestComplete: (DueRow) -> Unit,
    onRequestDefer: (DueRow) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Inbox,
            title = stringResource(R.string.due_segment_empty_title),
            body = stringResource(R.string.due_segment_empty),
            actionLabel = stringResource(R.string.due_filter_clear).takeIf { filtersActive },
            onAction = onClearFilters.takeIf { filtersActive },
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.instanceId }) { row ->
            SwipeableDueRow(
                row = row,
                onOpenRow = onOpenRow,
                onRequestComplete = onRequestComplete,
                onRequestDefer = onRequestDefer,
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDueRow(
    row: DueRow,
    onOpenRow: (DueRow) -> Unit,
    onRequestComplete: (DueRow) -> Unit,
    onRequestDefer: (DueRow) -> Unit,
) {
    val doneLabel = stringResource(R.string.due_swipe_done)
    val deferLabel = stringResource(R.string.due_swipe_defer)
    val dismissState = rememberSwipeToDismissBoxState(
        // Both directions open a dialog and the row springs back: a swipe on deck must never be
        // the only confirmation for a record that ends up in a survey file (C10).
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onRequestComplete(row)
                SwipeToDismissBoxValue.EndToStart -> onRequestDefer(row)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeActionBackground(
                    text = doneLabel,
                    color = ConditionColors.Good,
                    alignment = Alignment.Start,
                    modifier = Modifier.fillMaxSize(),
                )

                SwipeToDismissBoxValue.EndToStart -> SwipeActionBackground(
                    text = deferLabel,
                    color = ConditionColors.Monitor,
                    alignment = Alignment.End,
                    modifier = Modifier.fillMaxSize(),
                )

                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        DueListRow(row = row, onClick = { onOpenRow(row) })
    }
}

/** One work-list line, on the shared row — symbol, monospace tag, meta line, day-delta chip. */
@Composable
private fun DueListRow(row: DueRow, onClick: () -> Unit) {
    val turkish = isTurkishLocale()
    val delta = deltaLabel(row.dayDelta)
    val dueIso = Dates.formatIso(row.dueDate)
    val semantics = stringResource(
        R.string.due_row_semantics,
        row.tag,
        row.taskTitle.resolve(turkish),
        dueIso,
        delta,
    )
    val meta = listOf(dueIso, row.deckShortName, labelOf(row.performedBy))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    DeckWatchListRow(
        title = row.tag,
        titleIsTag = true,
        subtitle = "${row.taskTitle.resolve(turkish)}\n$meta",
        onClick = onClick,
        leading = { SymbolTile(symbolKey = row.symbolKey, size = SymbolSize) },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
            ) {
                DueDeltaChip(daysUntilDue = row.dayDelta, text = delta)
                if (row.status == TaskStatus.SKIPPED) {
                    TaskStatusChip(
                        status = TaskStatus.SKIPPED,
                        text = stringResource(R.string.due_deferred),
                    )
                }
            }
        },
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = semantics },
    )
}

/**
 * Survey prep — §12: a separate mode, not a variant of the list. It answers one question, "what
 * must happen before the certificate expires, and who is allowed to do it", so it carries its own
 * banner, its own section headers and a plain-language note on the shore-provider split.
 */
@Composable
private fun SurveyPrepContent(
    state: DueUiState,
    onLeave: () -> Unit,
    onOpenRow: (DueRow) -> Unit,
) {
    val prep = state.surveyPrep
    if (prep == null) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.FactCheck,
            title = stringResource(R.string.due_survey_no_expiry_title),
            body = stringResource(R.string.due_survey_no_expiry),
            actionLabel = stringResource(R.string.due_survey_exit),
            onAction = onLeave,
        )
        return
    }
    if (prep.shipStaff.isEmpty() && prep.shoreProvider.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.DoneAll,
            title = stringResource(R.string.due_survey_nothing_title),
            body = stringResource(R.string.due_survey_nothing),
            actionLabel = stringResource(R.string.due_survey_exit),
            onAction = onLeave,
        )
        return
    }
    val turkish = isTurkishLocale()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "mode-banner") {
            SurveyPrepBanner(
                expiryLine = stringResource(
                    R.string.due_survey_expiry,
                    Dates.formatIso(prep.certExpiry),
                    prep.daysToExpiry,
                ),
                onLeave = onLeave,
            )
        }
        if (prep.shoppingList.isNotEmpty()) {
            item(key = "shopping-header") {
                SectionHeader(text = stringResource(R.string.due_survey_shopping))
            }
            items(prep.shoppingList, key = { "shop-${it.taskKey}" }) { entry ->
                DeckWatchListRow(
                    title = entry.title.resolve(turkish),
                    subtitle = labelOf(entry.performedBy),
                    trailing = {
                        StatusChip(
                            text = stringResource(R.string.due_survey_count, entry.count),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            item(key = "shopping-divider") { HorizontalDivider() }
        }
        surveySection(
            title = R.string.due_survey_ship_staff,
            rows = prep.shipStaff,
            onOpenRow = onOpenRow,
        )
        surveySection(
            title = R.string.due_survey_shore,
            rows = prep.shoreProvider,
            note = R.string.due_survey_shore_hint,
            onOpenRow = onOpenRow,
        )
    }
}

@Composable
private fun SurveyPrepBanner(expiryLine: String, onLeave: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetPrimary)
                .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.due_survey_mode),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(text = expiryLine, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                onClick = onLeave,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.due_survey_exit))
            }
        }
    }
}

private fun LazyListScope.surveySection(
    @StringRes title: Int,
    rows: List<DueRow>,
    onOpenRow: (DueRow) -> Unit,
    @StringRes note: Int? = null,
) {
    item(key = "header-$title") {
        SectionHeader(
            text = stringResource(title),
            trailing = {
                StatusChip(
                    text = rows.size.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
    if (note != null) {
        item(key = "note-$title") {
            Text(
                text = stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Dimens.SpacingL,
                    end = Dimens.SpacingL,
                    bottom = Dimens.SpacingS,
                ),
            )
        }
    }
    items(rows, key = { "$title-${it.instanceId}" }) { row ->
        DueListRow(row = row, onClick = { onOpenRow(row) })
        HorizontalDivider()
    }
}

/** The localised chrome of the plaintext export — C8 keeps the words in `strings.xml`. */
@Composable
private fun rememberExportLabels(segmentName: String): DueExportLabels = DueExportLabels(
    header = stringResource(R.string.insp_export_header),
    vesselLabel = stringResource(R.string.insp_export_vessel),
    segmentLabel = stringResource(R.string.insp_export_segment),
    segmentName = segmentName,
    filtersLabel = stringResource(R.string.insp_export_filters),
    filtersNone = stringResource(R.string.insp_action_none),
    generatedLabel = stringResource(R.string.insp_export_generated),
    surveyLabel = stringResource(R.string.insp_export_survey),
    columnTag = stringResource(R.string.insp_export_col_tag),
    columnDeck = stringResource(R.string.insp_export_col_deck),
    columnTask = stringResource(R.string.insp_export_col_task),
    columnDue = stringResource(R.string.insp_export_col_due),
    columnDays = stringResource(R.string.insp_export_col_days),
    columnBy = stringResource(R.string.insp_export_col_by),
    totalLabel = stringResource(R.string.insp_export_total),
    emptyLabel = stringResource(R.string.insp_export_empty),
    performedByNames = PerformedBy.entries.associateWith { labelOf(it) },
)

private val SymbolSize = 40.dp
