package com.deckwatch.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.SearchOff
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SearchField
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationSection

/**
 * What is left of the module's own widgets after the DESIGN_OVERHAUL pass.
 *
 * The header, the search field, the empty state, the list headings and the plain meta chip all
 * moved to `core-designsystem` (`DeckWatchTopBar`, `SearchField`, `EmptyState`, `SectionHeader`,
 * `StatusChip`) — rule 2 and the overhaul's definition of done. Only the two things the design
 * system has no equivalent for stay here: the favourite star and the FLAG filter chip.
 */

/** The favourite star — filled amber when set, outlined-weight grey when not. */
@Composable
internal fun FavouriteButton(
    isFavourite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(Dimens.TouchTargetMin)) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(
                if (isFavourite) R.string.notes_action_unfavourite else R.string.notes_action_favourite,
            ),
            tint = if (isFavourite) {
                ConditionColors.Monitor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DimStarAlpha)
            },
        )
    }
}

/** A selectable filter chip; M3's own FilterChip is avoided so the 48dp target is guaranteed. */
@Composable
internal fun NotesFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .clip(RoundedCornerShape(Dimens.ChipCorner))
            .background(container)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = Dimens.SpacingM),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One glanceable icon per section, so the six home cards are told apart before the label is read
 * (rule 3: the officer is wearing gloves and in a hurry).
 */
internal fun sectionIcon(section: RegulationSection): ImageVector = when (section) {
    RegulationSection.SOLAS -> Icons.AutoMirrored.Filled.MenuBook
    RegulationSection.LSA -> Icons.Filled.Sailing
    RegulationSection.FFE -> Icons.Filled.LocalFireDepartment
    RegulationSection.HELIDECK -> Icons.Filled.Flight
    RegulationSection.ISGOTT -> Icons.Filled.LocalGasStation
    RegulationSection.IAMSAR -> Icons.Filled.Sos
    RegulationSection.FLAG -> Icons.Filled.Flag
    RegulationSection.CLASS -> Icons.AutoMirrored.Filled.FactCheck
    RegulationSection.MY_NOTES -> Icons.Filled.EditNote
}

private const val DimStarAlpha = 0.45f

/*
 * The equipment guide (§9.1) was written against this module's own small components, and the rest
 * of the tab has since moved onto the shared design system. These four are the seam: one place
 * that says which shared component each of the guide's helpers now means, rather than the guide's
 * screens each growing their own answer.
 */

/** The guide's search box — the shared [SearchField] with this tab's clear label and inset. */
@Composable
internal fun NotesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        clearContentDescription = stringResource(R.string.notes_search_clear),
        modifier = modifier.padding(
            start = Dimens.SpacingM,
            end = Dimens.SpacingM,
            top = Dimens.SpacingS,
            bottom = Dimens.SpacingS,
        ),
    )
}

/**
 * "Nothing here" inside the guide.
 *
 * The caller supplies one sentence, so the shared [EmptyState]'s title carries it and the body is
 * left empty rather than padded with a second sentence nobody wrote.
 */
@Composable
internal fun NotesEmptyState(text: String, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.SearchOff,
        title = text,
        body = "",
        modifier = modifier,
    )
}

/** A heading between groups in the guide — the shared [SectionHeader]. */
@Composable
internal fun ListSectionHeading(text: String, modifier: Modifier = Modifier) {
    SectionHeader(text = text, modifier = modifier)
}

/**
 * A small fact about a task — its interval, or who performs it.
 *
 * Outlined and in the secondary colour on purpose: saturated colour in this app means condition,
 * and an interval is not a condition.
 */
@Composable
internal fun MetaChip(text: String, modifier: Modifier = Modifier) {
    StatusChip(
        text = text,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    )
}
