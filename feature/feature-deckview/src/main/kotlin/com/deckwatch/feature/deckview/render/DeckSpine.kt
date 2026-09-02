package com.deckwatch.feature.deckview.render

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.feature.deckview.model.DeckNode

/**
 * The deck spine of §7.2: a vertical rail on the right edge, one pill per deck with its short code,
 * a coloured dot for the worst condition on it and a badge counting overdue equipment. Tapping a
 * pill flies the camera to that deck — the fast path on a twenty-deck vessel.
 *
 * The rail is also the single-finger vertical swipe target of §7.1B: swiping up climbs to the deck
 * above, swiping down descends. Putting the swipe here rather than on the canvas keeps a one-finger
 * drag on the plan meaning "pan", which is what the gesture table says it means.
 */
@Composable
@Suppress("LongParameterList") // A rail with four independent callbacks and two selection inputs.
fun DeckSpine(
    decks: List<DeckNode>,
    modifier: Modifier = Modifier,
    focusedDeckId: String? = null,
    onSelect: (String) -> Unit = {},
    onSwipeToDeckAbove: () -> Unit = {},
    onSwipeToDeckBelow: () -> Unit = {},
    pillDescription: @Composable (DeckNode) -> String = { it.name },
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .widthIn(min = Dimens.TouchTargetMin)
            .verticalScroll(scroll)
            .pointerInput(decks.size) {
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        when {
                            accumulated <= -SWIPE_THRESHOLD_PX -> onSwipeToDeckAbove()
                            accumulated >= SWIPE_THRESHOLD_PX -> onSwipeToDeckBelow()
                        }
                    },
                ) { _, delta -> accumulated += delta }
            }
            .padding(vertical = Dimens.SpacingS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (deck in decks) {
            DeckPill(
                deck = deck,
                selected = deck.deckId == focusedDeckId,
                description = pillDescription(deck),
                onClick = { onSelect(deck.deckId) },
            )
        }
    }
}

@Composable
private fun DeckPill(
    deck: DeckNode,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .widthIn(min = Dimens.TouchTargetMin)
            .clip(RoundedCornerShape(Dimens.ChipCorner))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(Dimens.ChipCorner),
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        Box(
            modifier = Modifier
                .size(DOT_SIZE)
                .clip(CircleShape)
                .background(ConditionColors.of(deck.worstCondition)),
        )
        Text(
            text = deck.shortCode,
            style = tagTextStyle(),
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (deck.overdueCount > 0) {
            Box(
                modifier = Modifier
                    .size(BADGE_SIZE)
                    .clip(CircleShape)
                    .background(ConditionColors.OutOfService),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (deck.overdueCount > MAX_BADGE) "$MAX_BADGE+" else "${deck.overdueCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.surface,
                    maxLines = 1,
                )
            }
        }
    }
}

private val DOT_SIZE = 10.dp
private val BADGE_SIZE = 18.dp
private const val MAX_BADGE = 9
private const val SWIPE_THRESHOLD_PX = 90f
