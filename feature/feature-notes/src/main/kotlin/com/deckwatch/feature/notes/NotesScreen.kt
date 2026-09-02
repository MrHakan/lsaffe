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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.feature.notes.equipment.EquipmentGuideScreen
import com.deckwatch.feature.notes.equipment.EquipmentTypeDetailScreen

/**
 * Tab 1 — the regulatory notebook (§8).
 *
 * Entry point for the whole tab. It owns the tab chrome (the shared [DeckWatchTopBar], the
 * first-entry disclaimer banner and the permanent footer disclaimer — §8.5) and a one-level
 * internal navigation stack; each destination has its own `@HiltViewModel`.
 *
 * Callable with no arguments: the app shell calls `NotesScreen()`.
 *
 * ### Chrome — DESIGN_OVERHAUL rule 2
 *
 * One title line, a back button on every destination below Home, and at most two actions: the
 * interval quick reference (§8.3) and "search", which puts the cursor in the Home search field
 * rather than opening a second search surface (rule 9 — the field is already the first thing on
 * the screen; the action is for the officer who scrolled past it).
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
    // A stack, because the equipment guide is two deep (group, then one type): back has to pop one
    // step, not jump all the way home from a type page.
    val backStack = rememberSaveable(saver = NotesBackStackSaver) {
        mutableStateListOf<NotesDestination>()
    }
    val destination: NotesDestination = backStack.lastOrNull() ?: NotesDestination.Home
    val push: (NotesDestination) -> Unit = { backStack.add(it) }
    val pop: () -> Unit = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }
    var openCardRefKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openCardWithComposer by rememberSaveable { mutableStateOf(false) }
    var disclaimerAcknowledged by rememberSaveable { mutableStateOf(false) }

    // Bumped by the top bar's search action; the Home screen focuses its field on every change.
    var focusSearchSignal by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = backStack.isNotEmpty(), onBack = pop)

    Column(modifier = modifier.fillMaxSize()) {
        DeckWatchTopBar(
            title = headerTitle(destination),
            onBack = pop.takeIf { backStack.isNotEmpty() },
            backContentDescription = stringResource(R.string.notes_action_back),
            actions = {
                if (destination == NotesDestination.Home) {
                    IconButton(
                        onClick = { focusSearchSignal++ },
                        modifier = Modifier.size(Dimens.TouchTargetMin),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.notes_action_search),
                        )
                    }
                    IconButton(
                        onClick = { push(NotesDestination.Intervals) },
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
                    onSectionClick = { section -> push(NotesDestination.Section(section)) },
                    onCardClick = { refKey ->
                        openCardRefKey = refKey
                        openCardWithComposer = false
                    },
                    onEquipmentGuideClick = { push(NotesDestination.Equipment()) },
                    focusSearchSignal = focusSearchSignal,
                )

                NotesDestination.Intervals -> IntervalMatrixScreen(
                    onCardClick = { refKey ->
                        openCardRefKey = refKey
                        openCardWithComposer = false
                    },
                )

                is NotesDestination.Equipment -> EquipmentGuideScreen(
                    group = current.group,
                    onOpenGroup = { group -> push(NotesDestination.Equipment(group)) },
                    onOpenType = { typeKey -> push(NotesDestination.TypeDetail(typeKey)) },
                )

                is NotesDestination.TypeDetail -> EquipmentTypeDetailScreen(
                    typeKey = current.typeKey,
                    onCardClick = { refKey -> openCardRefKey = refKey },
                )

                is NotesDestination.Section -> when (current.section) {
                    RegulationSection.MY_NOTES -> MyNotesScreen(
                        onCardClick = { refKey ->
                            openCardRefKey = refKey
                            openCardWithComposer = false
                        },
                    )

                    else -> SectionListScreen(
                        section = current.section,
                        onCardClick = { refKey ->
                            openCardRefKey = refKey
                            openCardWithComposer = false
                        },
                        onAddNoteForCard = { refKey ->
                            openCardRefKey = refKey
                            openCardWithComposer = true
                        },
                        onShowEquipmentForCard = onShowEquipmentForCard,
                        onFavouriteToggled = onFavouriteToggled,
                        // LSA and FFE are also catalogue groups, so those two sections offer the
                        // equipment guide beside their rules; the rest have no equipment of their own.
                        onOpenEquipmentGroup = { group -> push(NotesDestination.Equipment(group)) },
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
            onDismiss = {
                openCardRefKey = null
                openCardWithComposer = false
            },
            startWithComposer = openCardWithComposer,
            onShowEquipmentForCard = onShowEquipmentForCard,
        )
    }
}

@Composable
private fun headerTitle(destination: NotesDestination): String = when (destination) {
    NotesDestination.Home -> stringResource(R.string.notes_title)
    NotesDestination.Intervals -> stringResource(R.string.notes_intervals_title)
    is NotesDestination.Section -> stringResource(sectionTitleRes(destination.section))
    is NotesDestination.Equipment -> destination.group
        ?.let { stringResource(equipmentGroupLabel(it)) }
        ?: stringResource(R.string.notes_section_equipment)
    // The type's own name would be better, but the header renders before the record loads; the
    // page itself carries the name at the top.
    is NotesDestination.TypeDetail -> stringResource(R.string.notes_section_equipment)
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

/** Kept out of the shared [NotesComponents] file: only the banner and the footer use these. */
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
