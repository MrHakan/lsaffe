package com.deckwatch.feature.inspection

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.common.Dates
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import kotlinx.coroutines.launch

/**
 * The Due work list — §12. A dense, swipeable list of what is owed, not a dashboard.
 *
 * Swipe **right** marks a job done (through the completion dialog of §6.6); swipe **left** defers it
 * with a reason. Both gestures snap back and open a dialog rather than committing blind: this data
 * ends up in a survey file, so nothing destructive happens on a gesture alone (C10).
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
                EmptyHint(text = stringResource(R.string.due_no_vessel))
                return@Column
            }

            SegmentChips(
                selected = state.segment,
                counts = state.counts,
                onSelect = viewModel::selectSegment,
            )
            DueFilterRow(state = state, viewModel = viewModel)
            HorizontalDivider()

            when {
                state.surveyPrepEnabled -> SurveyPrepContent(
                    state = state,
                    onOpenEquipment = onOpenEquipment,
                )

                // Nothing anywhere and nothing filtered out: this vessel has no work on file yet.
                state.counts.values.sum() == 0 && !state.filters.isActive ->
                    EmptyHint(text = stringResource(R.string.due_empty_hint))

                else -> DueRowList(
                    rows = state.rows,
                    onOpenEquipment = onOpenEquipment,
                    onRequestComplete = { completing = it },
                    onRequestDefer = { deferring = it },
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.due_title), style = MaterialTheme.typography.titleLarge)
                if (state.vesselName.isNotEmpty()) {
                    Text(
                        text = state.vesselName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleSurveyPrep) {
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
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.due_copy_text),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
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

@Composable
private fun DueFilterRow(state: DueUiState, viewModel: DueViewModel) {
    val options = state.options
    val filters = state.filters
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.SpacingM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "deck") {
            FilterDropdownChip(
                label = stringResource(R.string.due_filter_deck),
                selectedLabel = options.decks.firstOrNull { it.id == filters.deckId }?.label,
                options = options.decks,
                optionLabel = { it.label },
                onSelect = { viewModel.setDeckFilter(it?.id) },
            )
        }
        item(key = "zone") {
            FilterDropdownChip(
                label = stringResource(R.string.due_filter_zone),
                selectedLabel = options.zones.firstOrNull { it.id == filters.zoneId }?.label,
                options = options.zones,
                optionLabel = { it.label },
                onSelect = { viewModel.setZoneFilter(it?.id) },
            )
        }
        item(key = "category") {
            FilterDropdownChip(
                label = stringResource(R.string.due_filter_category),
                selectedLabel = options.categories.firstOrNull { it.id == filters.categoryId }?.label,
                options = options.categories,
                optionLabel = { it.label },
                onSelect = { viewModel.setCategoryFilter(it?.id) },
            )
        }
        item(key = "group") {
            FilterDropdownChip(
                label = stringResource(R.string.due_filter_group),
                selectedLabel = filters.group?.let { labelOf(it) },
                options = options.groups,
                optionLabel = { labelOf(it) },
                onSelect = { viewModel.setGroupFilter(it) },
            )
        }
        item(key = "performer") {
            FilterDropdownChip(
                label = stringResource(R.string.due_filter_performed_by),
                selectedLabel = filters.performedBy?.let { labelOf(it) },
                options = options.performers,
                optionLabel = { labelOf(it) },
                onSelect = { viewModel.setPerformedByFilter(it) },
            )
        }
        item(key = "condition") {
            FilterDropdownChip(
                label = stringResource(R.string.due_filter_condition),
                selectedLabel = filters.condition?.let { labelOf(it) },
                options = options.conditions,
                optionLabel = { labelOf(it) },
                onSelect = { viewModel.setConditionFilter(it) },
            )
        }
        if (filters.isActive) {
            item(key = "clear") {
                TextButton(onClick = viewModel::clearFilters) {
                    Text(stringResource(R.string.due_filter_clear))
                }
            }
        }
    }
}

@Composable
private fun DueRowList(
    rows: List<DueRow>,
    onOpenEquipment: (String) -> Unit,
    onRequestComplete: (DueRow) -> Unit,
    onRequestDefer: (DueRow) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyHint(text = stringResource(R.string.due_segment_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.instanceId }) { row ->
            SwipeableDueRow(
                row = row,
                onOpenEquipment = onOpenEquipment,
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
    onOpenEquipment: (String) -> Unit,
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
                )

                SwipeToDismissBoxValue.EndToStart -> SwipeActionBackground(
                    text = deferLabel,
                    color = ConditionColors.Monitor,
                    alignment = Alignment.End,
                )

                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        DueRowContent(row = row, onClick = { onOpenEquipment(row.equipmentId) })
    }
}

@Composable
private fun DueRowContent(row: DueRow, onClick: () -> Unit) {
    val turkish = isTurkishLocale()
    val statusColor = ConditionColors.of(row.status)
    val delta = deltaLabel(row.dayDelta)
    val performer = labelOf(row.performedBy)
    val semantics = stringResource(
        R.string.due_row_semantics,
        row.tag,
        row.taskTitle.resolve(turkish),
        Dates.formatIso(row.dueDate),
        delta,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.ListRowComfortable)
            .padding(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS)
            .semantics { contentDescription = semantics },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        StatusRail(color = statusColor)
        SymbolTile(symbolKey = row.symbolKey, size = SymbolSize)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TagText(tag = row.tag)
                if (row.deckShortName.isNotBlank()) {
                    InfoChip(text = row.deckShortName)
                }
                if (row.status == TaskStatus.SKIPPED) {
                    InfoChip(
                        text = stringResource(R.string.due_deferred),
                        color = ConditionColors.Defective.copy(alpha = 0.18f),
                        contentColor = ConditionColors.Defective,
                    )
                }
            }
            Text(
                text = row.taskTitle.resolve(turkish),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Dates.formatIso(row.dueDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = delta,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
                InfoChip(text = performer)
            }
        }
    }
}

/** Survey prep — §12: the pre-survey workload split by who can legally do it. */
@Composable
private fun SurveyPrepContent(state: DueUiState, onOpenEquipment: (String) -> Unit) {
    val prep = state.surveyPrep
    if (prep == null) {
        EmptyHint(text = stringResource(R.string.due_survey_no_expiry))
        return
    }
    if (prep.shipStaff.isEmpty() && prep.shoreProvider.isEmpty()) {
        EmptyHint(text = stringResource(R.string.due_survey_nothing))
        return
    }
    val turkish = isTurkishLocale()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "expiry") {
            Text(
                text = stringResource(
                    R.string.due_survey_expiry,
                    Dates.formatIso(prep.certExpiry),
                    prep.daysToExpiry,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM),
            )
        }
        if (prep.shoppingList.isNotEmpty()) {
            item(key = "shopping-header") {
                SectionHeader(text = stringResource(R.string.due_survey_shopping))
            }
            items(prep.shoppingList, key = { "shop-${it.taskKey}" }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.title.resolve(turkish),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    InfoChip(text = labelOf(entry.performedBy))
                    Text(
                        text = stringResource(R.string.due_survey_count, entry.count),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item(key = "shopping-divider") { HorizontalDivider() }
        }
        surveySection(
            title = R.string.due_survey_ship_staff,
            rows = prep.shipStaff,
            onOpenEquipment = onOpenEquipment,
        )
        surveySection(
            title = R.string.due_survey_shore,
            rows = prep.shoreProvider,
            onOpenEquipment = onOpenEquipment,
        )
    }
}

private fun LazyListScope.surveySection(
    @StringRes title: Int,
    rows: List<DueRow>,
    onOpenEquipment: (String) -> Unit,
) {
    item(key = "header-$title") {
        SectionHeader(text = stringResource(title), trailing = rows.size.toString())
    }
    items(rows, key = { "$title-${it.instanceId}" }) { row ->
        DueRowContent(row = row, onClick = { onOpenEquipment(row.equipmentId) })
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

private val SymbolSize = 36.dp
