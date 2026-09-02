package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.ListDensity

/** Set by the app from user preferences; rows pick their height from it. */
val LocalListDensity = compositionLocalOf { ListDensity.COMPACT }

@Composable
fun listRowMinHeight(): Dp = when (LocalListDensity.current) {
    ListDensity.COMPACT -> Dimens.ListRowCompact
    ListDensity.COMFORTABLE -> Dimens.ListRowComfortable
}

/**
 * The standard list row — DESIGN_OVERHAUL rule 3. 56dp / 72dp by density,
 * leading slot (symbol tile, dot), title + subtitle, trailing slot (chip,
 * count, chevron).
 */
@Composable
fun DeckWatchListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    titleIsTag: Boolean = false,
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = listRowMinHeight())
            .then(clickable)
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(modifier = Modifier.padding(end = Dimens.SpacingM)) { leading() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (titleIsTag) com.deckwatch.core.designsystem.theme.tagTextStyle() else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = Dimens.SpacingS)) { trailing() }
        }
    }
}

/** A section heading inside lists and sheets. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingL, vertical = Dimens.SpacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Box(modifier = Modifier.width(IntrinsicWidth)) { trailing() }
        }
    }
}

private val IntrinsicWidth: Dp = Dp.Unspecified
