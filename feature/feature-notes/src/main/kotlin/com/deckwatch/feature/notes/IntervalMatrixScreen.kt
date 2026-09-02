package com.deckwatch.feature.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SearchField
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle

/**
 * The interval quick reference — §8.3.
 *
 * A matrix of *equipment type × interval × performed by*, assembled from the equipment catalogue,
 * the task definitions and the regulation cards. Rows group under their equipment type with a
 * sticky heading; the column header is pinned above the list and shares one horizontal scroll
 * state with every row, so the columns stay in register. Rows are a full 56dp (rule 3) and tapping
 * one opens the card behind the task.
 *
 * A star marks a task whose definition carries `flagOverrides` — the officer's cue that RMI,
 * Liberia or Panama does not simply follow the base rule (§11.5). It carries a
 * `contentDescription` naming the diverging Administrations, because the star is the only signal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IntervalMatrixScreen(
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntervalMatrixViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val columnScroll = rememberScrollState()
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.notes_intervals_filter_hint),
            clearContentDescription = stringResource(R.string.notes_search_clear),
            modifier = Modifier.padding(
                horizontal = Dimens.SpacingM,
                vertical = Dimens.SpacingS,
            ),
        )

        if (state.isEmpty) {
            MatrixEmptyState(
                query = state.query.trim(),
                filtering = state.isFilteredToNothing,
                onClearFilter = { viewModel.onQueryChange("") },
            )
            return@Column
        }

        MatrixHeaderRow(scrollState = columnScroll)
        HorizontalDivider()

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            state.groups.forEach { group ->
                stickyHeader(key = "group-${group.typeKey ?: "orphans"}") {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        SectionHeader(
                            text = group.typeName.ifBlank {
                                stringResource(R.string.notes_intervals_other_tasks)
                            },
                        )
                    }
                }
                items(items = group.rows, key = { "${group.typeKey}/${it.taskKey}" }) { row ->
                    MatrixRow(
                        row = row,
                        scrollState = columnScroll,
                        onClick = { row.cardRefKey?.let(onCardClick) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Rule 7: an empty matrix says which of the two emptinesses it is, and offers the way out. */
@Composable
private fun MatrixEmptyState(
    query: String,
    filtering: Boolean,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filtering) {
        EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.notes_search_none_title),
            body = stringResource(R.string.notes_intervals_no_match, query),
            actionLabel = stringResource(R.string.notes_action_clear_filter),
            onAction = onClearFilter,
            modifier = modifier,
        )
    } else {
        EmptyState(
            icon = Icons.Filled.Schedule,
            title = stringResource(R.string.notes_intervals_title),
            body = stringResource(R.string.notes_intervals_empty),
            modifier = modifier,
        )
    }
}

@Composable
private fun MatrixHeaderRow(scrollState: ScrollState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS),
        ) {
            HeaderCell(stringResource(R.string.notes_intervals_col_task), TaskColumnWidth)
            HeaderCell(stringResource(R.string.notes_intervals_col_interval), IntervalColumnWidth)
            HeaderCell(stringResource(R.string.notes_intervals_col_by), PerformedByColumnWidth)
            HeaderCell(stringResource(R.string.notes_intervals_col_flag), FlagColumnWidth)
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .width(width)
            .padding(end = Dimens.SpacingS),
    )
}

@Composable
private fun MatrixRow(
    row: IntervalRow,
    scrollState: ScrollState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetPrimary)
            .clickable(enabled = row.cardRefKey != null, onClick = onClick)
            .horizontalScroll(scrollState)
            .padding(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(TaskColumnWidth)
                .padding(end = Dimens.SpacingS),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.cardCitation?.let { citation ->
                Text(
                    text = citation,
                    style = tagTextStyle(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .width(IntervalColumnWidth)
                .padding(end = Dimens.SpacingS),
        ) {
            StatusChip(
                text = intervalLabel(row.intervalKind, row.intervalMonths),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(
            modifier = Modifier
                .width(PerformedByColumnWidth)
                .padding(end = Dimens.SpacingS),
        ) {
            StatusChip(
                text = stringResource(performedByRes(row.performedBy)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier.width(FlagColumnWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (row.hasFlagDivergence) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(
                            R.string.notes_intervals_divergence,
                            row.divergentFlags.joinToString(separator = " · "),
                        ),
                        tint = ConditionColors.Monitor,
                        modifier = Modifier.size(FlagMarkerSize),
                    )
                    Text(
                        text = row.divergentFlags.joinToString(separator = " "),
                        style = tagTextStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val TaskColumnWidth = 240.dp
private val IntervalColumnWidth = 132.dp
private val PerformedByColumnWidth = 180.dp
private val FlagColumnWidth = 120.dp
private val FlagMarkerSize = 18.dp
