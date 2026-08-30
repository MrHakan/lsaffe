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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.RegulationCardLabels
import com.deckwatch.core.designsystem.components.RegulationCardView
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RegulationSection

/**
 * One section's cards, rendered with the shared §8.2 card component.
 *
 * FLAG is the only section with sub-lists: a chip row selects RMI / Liberia / Panama, and the
 * default "All" view groups the cards under those headings — see [flagSubSection] for how a card
 * is assigned.
 *
 * LSA and FFE are the two sections that name a catalogue group as well as a body of rules, so they
 * carry a link into the equipment guide: an officer in the LSA section looking for "everything
 * about a lifebuoy" wants the equipment, not another list of citations.
 */
@Composable
internal fun SectionListScreen(
    section: RegulationSection,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onShowEquipmentForCard: (List<String>) -> Unit = {},
    onFavouriteToggled: (refKey: String, isFavourite: Boolean) -> Unit = { _, _ -> },
    onOpenEquipmentGroup: (EquipmentGroup) -> Unit = {},
    viewModel: SectionListViewModel = hiltViewModel(),
) {
    LaunchedEffect(section) { viewModel.setSection(section) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = regulationCardLabels()

    val catalogueGroup = section.catalogueGroup()

    Column(modifier = modifier.fillMaxSize()) {
        if (catalogueGroup != null) {
            EquipmentGuideLink(
                group = catalogueGroup,
                onClick = { onOpenEquipmentGroup(catalogueGroup) },
            )
        }

        if (state.showsFlagSubSections && state.availableFlags.isNotEmpty()) {
            FlagSubSectionChips(
                available = state.availableFlags,
                selected = state.flagFilter,
                onSelect = viewModel::setFlagFilter,
            )
        }

        if (state.isEmpty) {
            NotesEmptyState(text = stringResource(R.string.notes_section_empty))
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
                        ListSectionHeading(text = stringResource(flagSubSectionRes(group.flag)))
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
                    )
                }
            }
        }
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
            if (card.appliesToTypeKeys.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
                    TextButton(onClick = onShowEquipment) {
                        Text(stringResource(R.string.notes_action_show_equipment))
                    }
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

/**
 * The section that also names a catalogue group, or null. Only LSA and FFE do: SOLAS is a
 * convention, FLAG and CLASS are sources, and ISGOTT / IAMSAR / helicopter operations are guidance
 * about doing things rather than about a category of equipment.
 */
private fun RegulationSection.catalogueGroup(): EquipmentGroup? = when (this) {
    RegulationSection.LSA -> EquipmentGroup.LSA
    RegulationSection.FFE -> EquipmentGroup.FFE
    else -> null
}

/** One row at the top of the section: "browse this group's equipment instead". */
@Composable
private fun EquipmentGuideLink(group: EquipmentGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingM, vertical = Dimens.SpacingS)
            .heightIn(min = Dimens.TouchTargetMin)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.guide_tab_equipment),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.guide_section_link,
                        stringResource(equipmentGroupLabel(group)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}
