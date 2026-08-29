package com.deckwatch.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.RegulationSection

/**
 * The tab's own header. The app shell gives the Notes tab no top app bar (it owns only the four
 * bottom destinations, §5), so the header, the back affordance and the header action all live here.
 */
@Composable
internal fun NotesHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTargetPrimary)
                    .padding(horizontal = Dimens.SpacingS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.notes_action_back),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Dimens.SpacingS),
                )
                actions()
            }
            HorizontalDivider()
        }
    }
}

/** Global search over citation / title / WHAT — §8.1. */
@Composable
internal fun NotesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.notes_search_hint),
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.TouchTargetPrimary),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.notes_search_clear),
                    )
                }
            }
        },
        shape = RoundedCornerShape(Dimens.ChipCorner),
    )
}

/** One of the six section tiles — §8.1. */
@Composable
internal fun SectionTile(
    section: RegulationSection,
    count: Int,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(sectionTitleRes(section))
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SectionTileMinHeight)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title — $countLabel" },
        shape = RoundedCornerShape(Dimens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingM)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = count.toString(),
                    style = tagTextStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(sectionDescriptionRes(section)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )
        }
    }
}

/** A small labelled chip: the interval, the performing party, an attached citation. */
@Composable
internal fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    monospace: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.ChipCorner),
        color = container,
    ) {
        Text(
            text = text,
            style = if (monospace) tagTextStyle() else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
        )
    }
}

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

/** Every empty screen teaches: one sentence saying what belongs here — §14. */
@Composable
internal fun NotesEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpacingXl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A quiet section heading inside a list. */
@Composable
internal fun ListSectionHeading(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
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

private val SectionTileMinHeight = 108.dp
private const val DimStarAlpha = 0.45f
