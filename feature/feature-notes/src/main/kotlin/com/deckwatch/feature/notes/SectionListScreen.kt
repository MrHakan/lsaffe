package com.deckwatch.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.RegulationCardLabels
import com.deckwatch.core.designsystem.components.RegulationCardView
import com.deckwatch.core.designsystem.components.SearchField
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection

/**
 * One section's cards, rendered with the shared §8.2 card component.
 *
 * The sticky [SearchField] at the top filters the section without scrolling away (rule 9); it sits
 * outside the `LazyColumn` on purpose, so it stays reachable however far down the officer has
 * scrolled. FLAG is the only section with sub-lists: a chip row selects RMI / Liberia / Panama,
 * and the default "All" view groups the cards under those headings — see [flagSubSection] for how
 * a card is assigned.
 *
 * Each card carries the two §8.2 footer actions as 48dp text buttons and the favourite star in the
 * card's trailing header slot.
 */
@Composable
internal fun SectionListScreen(
    section: RegulationSection,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddNoteForCard: (String) -> Unit = onCardClick,
    onShowEquipmentForCard: (List<String>) -> Unit = {},
    onFavouriteToggled: (refKey: String, isFavourite: Boolean) -> Unit = { _, _ -> },
    viewModel: SectionListViewModel = hiltViewModel(),
) {
    LaunchedEffect(section) { viewModel.setSection(section) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = regulationCardLabels()

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.notes_section_filter_hint),
            clearContentDescription = stringResource(R.string.notes_search_clear),
            modifier = Modifier.padding(
                start = Dimens.SpacingM,
                end = Dimens.SpacingM,
                top = Dimens.SpacingS,
                bottom = Dimens.SpacingS,
            ),
        )

        if (state.showsFlagSubSections && state.availableFlags.isNotEmpty()) {
            FlagSubSectionChips(
                available = state.availableFlags,
                selected = state.flagFilter,
                onSelect = viewModel::setFlagFilter,
            )
        }

        if (state.isEmpty) {
            SectionEmptyState(section = section, query = state.query.trim(), filtering = state.isFilteredToNothing)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Dimens.SpacingM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingM),
        ) {
            state.groups.forEach { group ->
                if (state.showsFlagSubSections) {
                    item(key = "heading-${group.flag?.code ?: "other"}") {
                        SectionHeader(text = stringResource(flagSubSectionRes(group.flag)))
                    }
                }
                items(items = group.cards, key = { it.refKey }) { card ->
                    SectionCard(
                        card = card,
                        labels = labels,
                        appliesToNames = state.appliesToNames(card),
                        isFavourite = state.isFavourite(card.refKey),
                        onClick = { onCardClick(card.refKey) },
                        onFavouriteClick = {
                            onFavouriteToggled(card.refKey, viewModel.toggleFavourite(card.refKey))
                        },
                        onShowEquipment = { onShowEquipmentForCard(card.appliesToTypeKeys) },
                        onAddNote = { onAddNoteForCard(card.refKey) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionEmptyState(
    section: RegulationSection,
    query: String,
    filtering: Boolean,
    modifier: Modifier = Modifier,
) {
    if (filtering) {
        EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.notes_search_none_title),
            body = stringResource(R.string.notes_search_no_results, query),
            modifier = modifier,
        )
    } else {
        EmptyState(
            icon = sectionIcon(section),
            title = stringResource(sectionTitleRes(section)),
            body = stringResource(R.string.notes_section_empty),
            modifier = modifier,
        )
    }
}

@Composable
private fun SectionCard(
    card: RegulationCard,
    labels: RegulationCardLabels,
    appliesToNames: List<String>,
    isFavourite: Boolean,
    onClick: () -> Unit,
    onFavouriteClick: () -> Unit,
    onShowEquipment: () -> Unit,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RegulationCardView(
        card = card,
        modifier = modifier.clickable(onClick = onClick),
        labels = labels,
        appliesToNames = appliesToNames,
        trailingHeaderContent = {
            FavouriteButton(isFavourite = isFavourite, onClick = onFavouriteClick)
        },
        footerContent = {
            // §8.2's two card actions, side by side and each a full 48dp target. "Show my
            // equipment" stays visible but disabled when the card names no equipment type, so the
            // pair does not move around from card to card.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpacingS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                TextButton(
                    onClick = onShowEquipment,
                    enabled = card.appliesToTypeKeys.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Text(stringResource(R.string.notes_action_show_equipment))
                }
                TextButton(
                    onClick = onAddNote,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Text(stringResource(R.string.notes_action_add_note))
                }
            }
        },
    )
}

@Composable
private fun FlagSubSectionChips(
    available: List<FlagSubSection>,
    selected: FlagSubSection?,
    onSelect: (FlagSubSection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        NotesFilterChip(
            text = stringResource(R.string.notes_flag_all),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        available.forEach { flag ->
            NotesFilterChip(
                text = stringResource(flagSubSectionRes(flag)),
                selected = selected == flag,
                onClick = { onSelect(flag) },
            )
        }
    }
}
