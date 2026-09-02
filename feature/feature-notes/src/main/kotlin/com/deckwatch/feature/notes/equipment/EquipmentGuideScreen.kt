package com.deckwatch.feature.notes.equipment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.SymbolTile
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.feature.notes.NotesEmptyState
import com.deckwatch.feature.notes.NotesSearchField
import com.deckwatch.feature.notes.R
import com.deckwatch.feature.notes.equipmentGroupLabel

/**
 * The equipment guide index — §9.1.
 *
 * Two levels in one screen: with no group it lists the groups, with a group it lists that group's
 * types and offers a search box. The officer's mental model is "LSA → lifebuoy", so the group is a
 * step rather than a filter chip: the list of every type at once is too long to scan on deck.
 */
@Composable
internal fun EquipmentGuideScreen(
    group: EquipmentGroup?,
    onOpenGroup: (EquipmentGroup) -> Unit,
    onOpenType: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentGuideViewModel = hiltViewModel(),
) {
    LaunchedEffect(group) { viewModel.setGroup(group) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (group == null) {
        GroupList(state = state, onOpenGroup = onOpenGroup, modifier = modifier)
    } else {
        TypeList(
            state = state,
            onQueryChange = viewModel::setQuery,
            onOpenType = onOpenType,
            modifier = modifier,
        )
    }
}

@Composable
private fun GroupList(
    state: EquipmentGuideUiState,
    onOpenGroup: (EquipmentGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.loading && state.groups.isEmpty()) {
        NotesEmptyState(text = stringResource(R.string.guide_empty), modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpacingM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.SpacingS),
            )
        }
        items(items = state.groups, key = { it.group.name }) { summary ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTargetMin)
                    .clickable { onOpenGroup(summary.group) },
            ) {
                Column(modifier = Modifier.padding(Dimens.SpacingM)) {
                    Text(
                        text = stringResource(equipmentGroupLabel(summary.group)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.guide_type_count, summary.typeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeList(
    state: EquipmentGuideUiState,
    onQueryChange: (String) -> Unit,
    onOpenType: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        NotesSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.guide_search),
        )
        if (state.types.isEmpty() && !state.loading) {
            NotesEmptyState(
                text = if (state.query.isBlank()) {
                    stringResource(R.string.guide_empty)
                } else {
                    stringResource(R.string.guide_search_no_results, state.query.trim())
                },
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.SpacingM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        ) {
            items(items = state.types, key = { it.typeKey }) { row ->
                TypeRow(row = row, onClick = { onOpenType(row.typeKey) })
            }
        }
    }
}

@Composable
private fun TypeRow(row: EquipmentTypeRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolTile(symbolKey = row.symbolKey)
            Column(modifier = Modifier.padding(start = Dimens.SpacingM)) {
                Text(text = row.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = row.subGroup,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.guide_row_counts,
                        row.noteCount,
                        row.taskCount,
                        row.ruleCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
