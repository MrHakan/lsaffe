package com.deckwatch.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.RegulationCardView
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationSection

/**
 * Top level of the Notes tab — the section tiles with live card counts, the equipment guide, and a
 * global search that replaces them with matching cards as soon as the officer types (§8.1).
 */
@Composable
internal fun NotesHomeScreen(
    onSectionClick: (RegulationSection) -> Unit,
    onCardClick: (String) -> Unit,
    onEquipmentGuideClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        NotesSearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.padding(
                start = Dimens.SpacingM,
                end = Dimens.SpacingM,
                top = Dimens.SpacingS,
                bottom = Dimens.SpacingS,
            ),
        )

        if (state.isSearching) {
            SearchResults(state = state, onCardClick = onCardClick)
        } else {
            SectionGrid(
                state = state,
                onSectionClick = onSectionClick,
                onEquipmentGuideClick = onEquipmentGuideClick,
            )
        }
    }
}

@Composable
private fun SectionGrid(
    state: NotesHomeUiState,
    onSectionClick: (RegulationSection) -> Unit,
    onEquipmentGuideClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TileMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpacingM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        // The guide is first: an officer standing in front of a piece of kit wants the equipment,
        // and reaches the instrument through it rather than the other way round.
        item(key = "equipment-guide") {
            GuideTile(typeCount = state.equipmentTypeCount, onClick = onEquipmentGuideClick)
        }
        items(items = RegulationSection.entries, key = { it.name }) { section ->
            val count = state.countFor(section)
            SectionTile(
                section = section,
                count = count,
                countLabel = when (section) {
                    RegulationSection.MY_NOTES -> stringResource(R.string.notes_note_count, count)
                    else -> stringResource(R.string.notes_card_count, count)
                },
                onClick = { onSectionClick(section) },
            )
        }
    }
}

/**
 * The equipment guide's own tile. It is deliberately not a [RegulationSection]: the sections are
 * bodies of rules, and this is the catalogue seen from the other side.
 */
@Composable
private fun GuideTile(typeCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TileMinHeight)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Text(
                text = stringResource(R.string.notes_section_equipment),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.notes_section_equipment_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimens.SpacingXs),
            )
            Text(
                text = stringResource(R.string.guide_type_count, typeCount),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: NotesHomeUiState,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = regulationCardLabels()
    if (state.results.isEmpty()) {
        NotesEmptyState(
            text = stringResource(R.string.notes_search_no_results, state.query.trim()),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpacingM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        item(key = "count") {
            Text(
                text = stringResource(R.string.notes_search_results, state.results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(items = state.results, key = { it.refKey }) { card ->
            RegulationCardView(
                card = card,
                labels = labels,
                modifier = Modifier.clickable { onCardClick(card.refKey) },
            )
        }
    }
}

private val TileMinWidth = 160.dp
private val TileMinHeight = 96.dp
