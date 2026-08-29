package com.deckwatch.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.designsystem.theme.tagTextStyle
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.VerificationStatus

/**
 * The fixed-shape regulatory note card — §8.2. The WHAT / HOW OFTEN /
 * BY WHOM / EVIDENCE quadrant is mandatory on every card.
 *
 * Pure presentation: data in, events out. Shared by the Notes tab and the
 * equipment sheet's "Applicable requirements" dialog (§8.4).
 */
@Composable
fun RegulationCardView(
    card: RegulationCard,
    modifier: Modifier = Modifier,
    labels: RegulationCardLabels = RegulationCardLabels(),
    appliesToNames: List<String> = emptyList(),
    trailingHeaderContent: @Composable () -> Unit = {},
    footerContent: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimens.SpacingL)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CitationBadge(citation = card.citation)
                trailingHeaderContent()
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Dimens.SpacingS),
            )

            if (card.verificationStatus != VerificationStatus.VERIFIED) {
                VerificationStrip(
                    text = labels.verifyStrip,
                    modifier = Modifier.padding(top = Dimens.SpacingS),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingM))

            QuadrantRow(label = labels.what, value = card.what)
            QuadrantRow(label = labels.howOften, value = card.howOften, chip = true, chipColor = intervalColor(card.howOften))
            QuadrantRow(label = labels.byWhom, value = card.byWhom, chip = true, chipColor = MaterialTheme.colorScheme.secondaryContainer)
            QuadrantRow(label = labels.evidence, value = card.evidence)

            if (card.detailBullets.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingM))
                card.detailBullets.forEach { bullet ->
                    Row(modifier = Modifier.padding(vertical = Dimens.SpacingXs)) {
                        Text(text = "•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(text = bullet, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (card.flagNotes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingM))
                // Collapsible: three flag notes are three paragraphs, and only one flag is the
                // vessel's. The header row is the toggle, so the section folds away on a card an
                // officer is reading for the SOLAS text rather than the flag overlay.
                var flagNotesExpanded by rememberSaveable(card.refKey) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.TouchTargetMin)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = labels.flagNotes,
                            onClick = { flagNotesExpanded = !flagNotesExpanded },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = labels.flagNotes,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = card.flagNotes.keys.joinToString(" · "),
                        style = tagTextStyle(),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Icon(
                        imageVector = if (flagNotesExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (flagNotesExpanded) {
                    card.flagNotes.forEach { (flag, note) ->
                        Row(modifier = Modifier.padding(top = Dimens.SpacingXs)) {
                            Text(
                                text = flag,
                                style = tagTextStyle(),
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "  $note",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (card.revisionNote.isNotEmpty()) {
                    Text(
                        text = "${labels.revisionPrefix}: ${card.revisionNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.SpacingXs),
                    )
                }
            }

            if (appliesToNames.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpacingM))
                Text(
                    text = "${labels.appliesTo}: ${appliesToNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            footerContent()
        }
    }
}

@Composable
private fun CitationBadge(citation: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.ChipCorner),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = citation,
            style = tagTextStyle(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
        )
    }
}

@Composable
private fun VerificationStrip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.ChipCorner))
            .background(ConditionColors.Monitor.copy(alpha = 0.18f))
            .padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = ConditionColors.Monitor,
        )
    }
}

@Composable
private fun QuadrantRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    chip: Boolean = false,
    chipColor: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(QuadrantLabelWidth),
        )
        if (chip) {
            Surface(
                shape = RoundedCornerShape(Dimens.ChipCorner),
                color = if (chipColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceVariant else chipColor,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Dimens.SpacingS, vertical = Dimens.SpacingXs),
                )
            }
        } else {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val QuadrantLabelWidth = 92.dp

/** Interval chips are colour-coded by urgency of cadence — §8.2. */
@Composable
private fun intervalColor(howOften: String): Color {
    val normalized = howOften.lowercase()
    return when {
        "week" in normalized -> ConditionColors.Good.copy(alpha = 0.2f)
        "month" in normalized && "12" !in normalized -> ConditionColors.Acceptable.copy(alpha = 0.25f)
        "annual" in normalized || "year" in normalized || "12 month" in normalized -> ConditionColors.Monitor.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}
