package com.deckwatch.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationSection

/**
 * Tab 1 — the regulatory notebook (§8).
 *
 * Entry point for the whole tab. It owns the tab chrome (header, first-entry disclaimer banner,
 * permanent footer disclaimer — §8.5) and a one-level internal navigation stack; each destination
 * has its own `@HiltViewModel`.
 *
 * Callable with no arguments: the app shell calls `NotesScreen()`.
 *
 * @param onShowEquipmentForCard invoked with a card's `appliesToTypeKeys` when the officer asks to
 *   see their own equipment of those types (§8.2). Wired to the Vessel tab later.
 * @param onDisclaimerAccepted invoked once when the first-entry banner is acknowledged, so the
 *   "seen" flag can move to DataStore later. Until then the flag is `rememberSaveable` only.
 * @param onFavouriteToggled invoked with the card key and its new state after a favourite toggle;
 *   favourites are in-memory for now (§6 defines no table for them).
 */
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    onShowEquipmentForCard: (List<String>) -> Unit = {},
    onDisclaimerAccepted: () -> Unit = {},
    onFavouriteToggled: (refKey: String, isFavourite: Boolean) -> Unit = { _, _ -> },
    chromeViewModel: NotesChromeViewModel = hiltViewModel(),
) {
    val footerVisible by chromeViewModel.footerVisible.collectAsStateWithLifecycle()
    var confirmingFooterDismiss by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable(stateSaver = NotesDestinationSaver) {
        mutableStateOf<NotesDestination>(NotesDestination.Home)
    }
    var openCardRefKey by rememberSaveable { mutableStateOf<String?>(null) }
    var disclaimerAcknowledged by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = destination != NotesDestination.Home) {
        destination = NotesDestination.Home
    }

    Column(modifier = modifier.fillMaxSize()) {
        NotesHeader(
            title = headerTitle(destination),
            onBack = if (destination == NotesDestination.Home) {
                null
            } else {
                { destination = NotesDestination.Home }
            },
            actions = {
                if (destination == NotesDestination.Home) {
                    IconButton(
                        onClick = { destination = NotesDestination.Intervals },
                        modifier = Modifier.size(Dimens.TouchTargetMin),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = stringResource(R.string.notes_open_intervals),
                        )
                    }
                }
            },
        )

        if (!disclaimerAcknowledged) {
            DisclaimerBanner(
                onAcknowledge = {
                    disclaimerAcknowledged = true
                    onDisclaimerAccepted()
                },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val current = destination) {
                NotesDestination.Home -> NotesHomeScreen(
                    onSectionClick = { section -> destination = NotesDestination.Section(section) },
                    onCardClick = { refKey -> openCardRefKey = refKey },
                )

                NotesDestination.Intervals -> IntervalMatrixScreen(
                    onCardClick = { refKey -> openCardRefKey = refKey },
                )

                is NotesDestination.Section -> when (current.section) {
                    RegulationSection.MY_NOTES -> MyNotesScreen(
                        onCardClick = { refKey -> openCardRefKey = refKey },
                    )

                    else -> SectionListScreen(
                        section = current.section,
                        onCardClick = { refKey -> openCardRefKey = refKey },
                        onShowEquipmentForCard = onShowEquipmentForCard,
                        onFavouriteToggled = onFavouriteToggled,
                    )
                }
            }
        }

        if (footerVisible) {
            NotesFooterDisclaimer(onDismiss = { confirmingFooterDismiss = true })
        }
    }

    if (confirmingFooterDismiss) {
        ConfirmFooterDismissDialog(
            onConfirm = {
                confirmingFooterDismiss = false
                chromeViewModel.dismissFooter()
            },
            onDismiss = { confirmingFooterDismiss = false },
        )
    }

    openCardRefKey?.let { refKey ->
        CardDetailDialog(
            refKey = refKey,
            onDismiss = { openCardRefKey = null },
            onShowEquipmentForCard = onShowEquipmentForCard,
        )
    }
}

@Composable
private fun headerTitle(destination: NotesDestination): String = when (destination) {
    NotesDestination.Home -> stringResource(R.string.notes_title)
    NotesDestination.Intervals -> stringResource(R.string.notes_intervals_title)
    is NotesDestination.Section -> stringResource(sectionTitleRes(destination.section))
}

/**
 * Shown on first entry to the Notes tab — §8.5. Amber is the app's single "needs your attention"
 * accent (§14); the body is §17.6 verbatim.
 */
@Composable
private fun DisclaimerBanner(onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    val accent = ConditionColors.Monitor
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.SpacingM)
            .clip(RoundedCornerShape(Dimens.CardCorner))
            .background(accent.copy(alpha = BannerTintAlpha))
            .padding(Dimens.SpacingM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(BannerIconSize),
            )
            Text(
                text = stringResource(R.string.notes_disclaimer_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Dimens.SpacingS),
            )
        }
        Text(
            text = stringResource(R.string.notes_disclaimer_body),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Dimens.SpacingS),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.notes_disclaimer_acknowledge))
            }
        }
    }
}

/**
 * The disclaimer strip at the foot of the tab — §8.5. Never truncated: the wording is §17.6
 * verbatim and a shortened disclaimer is not the disclaimer.
 *
 * It can be dismissed, because repeating it under every screen costs a third of a phone display
 * on the tab an officer reads most. Dismissing it removes the repetition, not the disclaimer: the
 * first-entry banner still requires an acknowledgement on a fresh install, and More → About
 * carries the full text permanently.
 */
@Composable
private fun NotesFooterDisclaimer(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            HorizontalDivider()
            // The close button sits beside the text rather than above it: a row of its own would
            // make the strip taller than it already is, which is the thing being complained about.
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(R.string.notes_disclaimer_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = Dimens.SpacingM,
                            top = Dimens.SpacingS,
                            bottom = Dimens.SpacingS,
                        ),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(Dimens.TouchTargetMin),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.notes_disclaimer_hide),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Kept out of the shared [NotesComponents] file: only the banner uses these. */
private const val BannerTintAlpha = 0.16f
private val BannerIconSize = 20.dp

/**
 * Asks before the strip goes away for good, and says where the text stays reachable — hiding a
 * safety notice should be a deliberate act, not a mis-tap.
 */
@Composable
private fun ConfirmFooterDismissDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_disclaimer_hide_title)) },
        text = { Text(stringResource(R.string.notes_disclaimer_hide_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.notes_disclaimer_hide_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notes_disclaimer_hide_cancel))
            }
        },
    )
}
