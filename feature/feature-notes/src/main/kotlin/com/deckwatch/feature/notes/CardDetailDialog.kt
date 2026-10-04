package com.deckwatch.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import com.deckwatch.core.designsystem.components.DeckWatchTopBar
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.model.EquipmentType
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.RegulationCardLabels
import com.deckwatch.core.designsystem.components.RegulationCardView
import com.deckwatch.core.designsystem.components.StatusChip
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.UserNote
import com.deckwatch.core.model.VerificationStatus

/**
 * Full-screen reader retaining the originating screen and the history of related regulations.
 *
 * ### One primary action — DESIGN_OVERHAUL rule 1
 *
 * "Add my note" is a 56dp full-width button pinned under the scrolling card body; while the
 * composer is open the same button becomes "Save", so there is never a second button competing
 * with it. "Show my equipment" stays a quiet text button, and "Close" is the dialog's own
 * dismiss button.
 *
 * ### Provenance — §8.5
 *
 * Source, last-reviewed date and verification state read as [StatusChip]s rather than grey body
 * text, because whether a figure is verified is exactly the thing an officer must see at a glance.
 * The amber "verify against the current instrument" strip inside [RegulationCardView] stays.
 *
 * @param startWithComposer opens with the note composer already showing — used by the section
 *   list's "Add my note" footer button, which should not need a second tap.
 */
@Composable
internal fun CardDetailDialog(
    refKey: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    startWithComposer: Boolean = false,
    onShowEquipmentForCard: (List<String>) -> Unit = {},
    onOpenType: (String) -> Unit = {},
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = regulationCardLabels()

    /** Null while the composer is closed; a (possibly empty) draft while it is open. */
    var noteDraft by rememberSaveable(refKey) {
        mutableStateOf<String?>(if (startWithComposer) "" else null)
    }

    var readerHistory by rememberSaveable(refKey) { mutableStateOf(listOf(refKey)) }
    val readerKey = readerHistory.last()
    val back: () -> Unit = {
        if (readerHistory.size > 1) {
            readerHistory = readerHistory.dropLast(1)
            noteDraft = null
        } else {
            onDismiss()
        }
    }
    LaunchedEffect(readerKey) { viewModel.open(readerKey) }
    val card = state.card
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.safeDrawingPadding().imePadding()) {
                DeckWatchTopBar(
                    title = card?.citation ?: stringResource(R.string.notes_detail_title),
                    onBack = back,
                    backContentDescription = stringResource(R.string.notes_action_close),
                )
                if (card == null) {
                    Text(stringResource(R.string.notes_section_empty))
                } else {
                    CardDetailBody(
                        card = card,
                        appliesToNames = state.appliesToNames,
                        myNotes = state.myNotes,
                        equipmentTypes = state.equipmentTypes,
                        relatedCards = state.relatedCards,
                        onOpenType = onOpenType,
                        onOpenCard = { readerHistory = readerHistory + it; noteDraft = null },
                        labels = labels,
                        noteDraft = noteDraft,
                        onNoteDraftChange = { noteDraft = it },
                        onSaveNote = { body ->
                            viewModel.addNote(title = card.citation, body = body)
                            noteDraft = null
                        },
                        onShowEquipmentForCard = onShowEquipmentForCard,
                        modifier = Modifier.weight(1f).padding(Dimens.SpacingM),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardDetailBody(
    card: RegulationCard,
    appliesToNames: List<String>,
    myNotes: List<UserNote>,
    equipmentTypes: List<EquipmentType>,
    relatedCards: List<RegulationCard>,
    onOpenType: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    labels: RegulationCardLabels,
    noteDraft: String?,
    onNoteDraftChange: (String?) -> Unit,
    onSaveNote: (String) -> Unit,
    onShowEquipmentForCard: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(noteDraft != null) { if (noteDraft != null) scroll.scrollTo(0) }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        ) {
            if (noteDraft != null) {
                NoteComposer(
                    draft = noteDraft,
                    onDraftChange = onNoteDraftChange,
                    onCancel = { onNoteDraftChange(null) },
                )
            }

            RegulationCardView(card = card, labels = labels, appliesToNames = appliesToNames)

            Provenance(card = card)

            if (equipmentTypes.isNotEmpty()) {
                TextButton(
                    onClick = { onShowEquipmentForCard(equipmentTypes.map { it.typeKey }) },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Text(stringResource(R.string.notes_action_show_equipment))
                }
            }

            if (equipmentTypes.isNotEmpty()) {
                Text(stringResource(R.string.notes_context_equipment), style = MaterialTheme.typography.titleMedium)
                equipmentTypes.forEach { type ->
                    DeckWatchListRow(title = type.nameEn, subtitle = type.nameTr, onClick = { onOpenType(type.typeKey) })
                }
            }
            if (relatedCards.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.notes_context_rules), style = MaterialTheme.typography.titleMedium)
                relatedCards.forEach { related ->
                    DeckWatchListRow(title = related.citation, subtitle = related.title, onClick = { onOpenCard(related.refKey) })
                }
            }

            if (myNotes.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.notes_detail_my_notes_heading),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                myNotes.forEach { note -> AttachedNote(note) }
            }
        }

        // The one primary action, pinned below the scroll area so it is always in reach.
        Button(
            onClick = {
                if (noteDraft == null) onNoteDraftChange("") else onSaveNote(noteDraft)
            },
            enabled = noteDraft == null || noteDraft.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingM)
                .heightIn(min = Dimens.TouchTargetPrimary),
        ) {
            Text(
                stringResource(
                    if (noteDraft == null) R.string.notes_action_add_note else R.string.notes_action_save,
                ),
            )
        }
    }
}

/**
 * §8.5 — every card states the instrument it came from, when it was last checked and whether the
 * figure has been verified. Chips, not prose: rule 6.
 */
@Composable
private fun Provenance(card: RegulationCard, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
        if (card.sourceUrl.isNotBlank()) {
            TextButton(onClick = { uriHandler.openUri(card.sourceUrl) }) {
                Text(stringResource(R.string.notes_official_source))
            }
        }
        if (card.revisionNote.isNotBlank()) {
            Text(card.revisionNote, style = MaterialTheme.typography.bodySmall)
        }
        if (card.sourceRef.isNotBlank()) {
            StatusChip(
                text = stringResource(R.string.notes_detail_source, card.sourceRef),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (card.lastReviewed.isNotBlank()) {
            StatusChip(
                text = stringResource(R.string.notes_detail_reviewed, card.lastReviewed),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(
            text = stringResource(verificationLabelRes(card.verificationStatus)),
            color = when (card.verificationStatus) {
                VerificationStatus.VERIFIED -> ConditionColors.Good
                VerificationStatus.NEEDS_PERIODIC_REVIEW -> ConditionColors.Acceptable
                VerificationStatus.UNVERIFIED -> ConditionColors.Monitor
            },
        )
    }
}

@Composable
private fun NoteComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(stringResource(R.string.notes_detail_note_hint)) },
            minLines = ComposerMinLines,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.notes_action_cancel))
            }
        }
    }
}

@Composable
private fun AttachedNote(note: UserNote, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = Dimens.SpacingXs)) {
        if (note.title.isNotBlank()) {
            Text(text = note.title, style = MaterialTheme.typography.titleSmall)
        }
        Text(text = note.body, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val ComposerMinLines = 3
