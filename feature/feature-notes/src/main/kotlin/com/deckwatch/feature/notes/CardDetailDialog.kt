package com.deckwatch.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * The full card in a dialog — §8.4 wants a card readable without leaving the screen that opened it.
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
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(refKey) { viewModel.open(refKey) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = regulationCardLabels()

    /** Null while the composer is closed; a (possibly empty) draft while it is open. */
    var noteDraft by rememberSaveable(refKey) {
        mutableStateOf<String?>(if (startWithComposer) "" else null)
    }

    val card = state.card
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.notes_detail_title)) },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.notes_action_close))
            }
        },
        text = {
            if (card == null) {
                Text(stringResource(R.string.notes_section_empty))
            } else {
                CardDetailBody(
                    card = card,
                    appliesToNames = state.appliesToNames,
                    myNotes = state.myNotes,
                    labels = labels,
                    noteDraft = noteDraft,
                    onNoteDraftChange = { noteDraft = it },
                    onSaveNote = { body ->
                        viewModel.addNote(title = card.citation, body = body)
                        noteDraft = null
                    },
                    onShowEquipmentForCard = onShowEquipmentForCard,
                )
            }
        },
    )
}

@Composable
private fun CardDetailBody(
    card: RegulationCard,
    appliesToNames: List<String>,
    myNotes: List<UserNote>,
    labels: RegulationCardLabels,
    noteDraft: String?,
    onNoteDraftChange: (String?) -> Unit,
    onSaveNote: (String) -> Unit,
    onShowEquipmentForCard: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ScrollAreaMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
        ) {
            RegulationCardView(card = card, labels = labels, appliesToNames = appliesToNames)

            Provenance(card = card)

            if (card.appliesToTypeKeys.isNotEmpty()) {
                TextButton(
                    onClick = { onShowEquipmentForCard(card.appliesToTypeKeys) },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Text(stringResource(R.string.notes_action_show_equipment))
                }
            }

            if (noteDraft != null) {
                NoteComposer(
                    draft = noteDraft,
                    onDraftChange = onNoteDraftChange,
                    onCancel = { onNoteDraftChange(null) },
                )
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingXs),
    ) {
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
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
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

private val ScrollAreaMaxHeight = 440.dp
private const val ComposerMinLines = 3
