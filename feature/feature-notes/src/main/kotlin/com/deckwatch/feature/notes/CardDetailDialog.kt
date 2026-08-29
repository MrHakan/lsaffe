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
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.UserNote

/**
 * The full card in a dialog — §8.4 wants a card readable without leaving the screen that opened it.
 *
 * Carries the two card actions of §8.2: "Add my note" (a [UserNote] attached to this `refKey`) and
 * "Show my equipment", which hands the card's `appliesToTypeKeys` to the host.
 */
@Composable
internal fun CardDetailDialog(
    refKey: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShowEquipmentForCard: (List<String>) -> Unit = {},
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(refKey) { viewModel.open(refKey) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels = regulationCardLabels()

    /** Null while the composer is closed; a (possibly empty) draft while it is open. */
    var noteDraft by rememberSaveable(refKey) { mutableStateOf<String?>(null) }

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
            val card = state.card
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = DialogMaxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
    ) {
        RegulationCardView(card = card, labels = labels, appliesToNames = appliesToNames)

        Provenance(sourceRef = card.sourceRef, lastReviewed = card.lastReviewed)

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
            TextButton(
                onClick = { onNoteDraftChange(noteDraft ?: "") },
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.notes_action_add_note))
            }
            if (card.appliesToTypeKeys.isNotEmpty()) {
                TextButton(
                    onClick = { onShowEquipmentForCard(card.appliesToTypeKeys) },
                    modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
                ) {
                    Text(stringResource(R.string.notes_action_show_equipment))
                }
            }
        }

        if (noteDraft != null) {
            NoteComposer(
                draft = noteDraft,
                onDraftChange = onNoteDraftChange,
                onSave = { onSaveNote(noteDraft) },
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
}

/** §8.5 — every card states the instrument it came from and when it was last checked. */
@Composable
private fun Provenance(sourceRef: String, lastReviewed: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (sourceRef.isNotBlank()) {
            Text(
                text = stringResource(R.string.notes_detail_source, sourceRef),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lastReviewed.isNotBlank()) {
            Text(
                text = stringResource(R.string.notes_detail_reviewed, lastReviewed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
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
            TextButton(
                onClick = onSave,
                enabled = draft.isNotBlank(),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.notes_action_save))
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

private val DialogMaxHeight = 520.dp
private const val ComposerMinLines = 3
