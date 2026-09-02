package com.deckwatch.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SearchField
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection

/**
 * Top level of the Notes tab — DESIGN_OVERHAUL rule 9 ("search first, browse second").
 *
 * The shared [SearchField] is the very first thing on the screen and is keyboard-ready: the top
 * bar's search action bumps [focusSearchSignal], which puts the cursor in it and raises the
 * keyboard. Below it are the six sections as large tappable cards (rule 3) — icon, name, one line
 * of what is inside, and a count chip.
 *
 * As soon as the officer types, the cards give way to a result list of [DeckWatchListRow]s: the
 * citation reads as the title in the tag face, the card title is the subtitle and the section is a
 * trailing chip. The full §8.2 card is deliberately *not* rendered here — twenty full cards in a
 * result list is a wall of text; the card opens in a dialog on tap.
 */
@Composable
internal fun NotesHomeScreen(
    onSectionClick: (RegulationSection) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusSearchSignal: Int = 0,
    viewModel: NotesHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusSearchSignal) {
        if (focusSearchSignal > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.notes_search_hint),
            clearContentDescription = stringResource(R.string.notes_search_clear),
            modifier = Modifier
                .padding(
                    start = Dimens.SpacingM,
                    end = Dimens.SpacingM,
                    top = Dimens.SpacingS,
                    bottom = Dimens.SpacingS,
                )
                .focusRequester(focusRequester),
        )

        if (state.isSearching) {
            SearchResults(state = state, onCardClick = onCardClick)
        } else {
            SectionList(state = state, onSectionClick = onSectionClick)
        }
    }
}

@Composable
private fun SectionList(
    state: NotesHomeUiState,
    onSectionClick: (RegulationSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.SpacingM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
    ) {
        items(items = RegulationSection.entries, key = { it.name }) { section ->
            val count = state.countFor(section)
            SectionCard(
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
 * One of the six section cards — §8.1 through rule 3. `heightIn(min = …)` rather than a fixed
 * height so nothing clips at 200 % font scaling; the whole card is one target and one
 * announcement.
 */
@Composable
private fun SectionCard(
    section: RegulationSection,
    count: Int,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(sectionTitleRes(section))
    val description = stringResource(sectionDescriptionRes(section))
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SectionCardMinHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description. $countLabel"
            },
        shape = RoundedCornerShape(Dimens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SectionCardMinHeight)
                .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = sectionIcon(section),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SectionIconSize),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.SpacingL),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(
                text = count.toString(),
                color = MaterialTheme.colorScheme.primary,
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
    if (state.results.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.notes_search_none_title),
            body = stringResource(R.string.notes_search_no_results, state.query.trim()),
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "count") {
            SectionHeader(text = stringResource(R.string.notes_search_results, state.results.size))
        }
        items(items = state.results, key = { it.refKey }) { card ->
            SearchResultRow(card = card, onClick = { onCardClick(card.refKey) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SearchResultRow(
    card: RegulationCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeckWatchListRow(
        title = card.citation,
        modifier = modifier,
        subtitle = card.title,
        onClick = onClick,
        titleIsTag = true,
        trailing = {
            StatusChip(
                text = stringResource(sectionTitleRes(card.section)),
                color = MaterialTheme.colorScheme.secondary,
            )
        },
    )
}

private val SectionCardMinHeight = 80.dp
private val SectionIconSize = 28.dp
