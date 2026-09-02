package com.deckwatch.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.components.ConfirmDialog
import com.deckwatch.core.designsystem.components.DeckWatchListRow
import com.deckwatch.core.designsystem.components.EmptyState
import com.deckwatch.core.designsystem.components.SectionHeader
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.UserNote

/**
 * MY NOTES — the officer's own notes, grouped into folders, each optionally attached to a
 * regulation card — §8.1.
 *
 * Rows are the shared [DeckWatchListRow]: the note's title, then the folder and the attached
 * citation as the subtitle, and delete in the trailing slot. "New note" is the screen's single
 * primary action (rule 1) as a 56dp FAB; deleting goes through the shared [ConfirmDialog]
 * (rule 8).
 */
@Composable
internal fun MyNotesScreen(
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyNotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Editor state: `editing` holds the note being changed, `composingNew` opens a blank one.
    var editing by remember { mutableStateOf<UserNote?>(null) }
    var composingNew by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UserNote?>(null) }

    val unfiledLabel = stringResource(R.string.notes_my_folder_unfiled)

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Filled.EditNote,
                title = stringResource(R.string.notes_my_title),
                body = stringResource(R.string.notes_my_empty),
                actionLabel = stringResource(R.string.notes_my_new),
                onAction = { composingNew = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ListBottomInset),
            ) {
                state.folders.forEach { folder ->
                    item(key = "folder-${folder.name}") {
                        SectionHeader(text = folder.name.ifEmpty { unfiledLabel })
                    }
                    items(items = folder.notes, key = { it.id }) { note ->
                        UserNoteRow(
                            note = note,
                            subtitle = noteSubtitle(
                                note = note,
                                citation = state.citationFor(note),
                                unfiledLabel = unfiledLabel,
                            ),
                            onClick = { editing = note },
                            onDelete = { pendingDelete = note },
                            onOpenCard = note.regulationRefKey?.let { key -> { onCardClick(key) } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // Rule 1: the only primary action on this screen.
        if (!state.isEmpty) {
            ExtendedFloatingActionButton(
                onClick = { composingNew = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimens.SpacingL)
                    .heightIn(min = Dimens.TouchTargetPrimary),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.notes_my_new)) },
            )
        }
    }

    if (composingNew) {
        NoteEditorDialog(
            note = null,
            folderSuggestions = state.folderNames,
            onDismiss = { composingNew = false },
            onSave = { title, body, folder ->
                viewModel.createNote(title = title, body = body, folder = folder)
                composingNew = false
            },
        )
    }

    editing?.let { note ->
        NoteEditorDialog(
            note = note,
            folderSuggestions = state.folderNames,
            onDismiss = { editing = null },
            onSave = { title, body, folder ->
                viewModel.updateNote(note = note, title = title, body = body, folder = folder)
                editing = null
            },
        )
    }

    pendingDelete?.let { note ->
        ConfirmDialog(
            title = stringResource(R.string.notes_my_delete_title),
            body = stringResource(R.string.notes_my_delete_body, note.title),
            confirmLabel = stringResource(R.string.notes_action_delete),
            cancelLabel = stringResource(R.string.notes_action_cancel),
            onConfirm = {
                viewModel.deleteNote(note.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * Folder and citation, in that order — the two things that place a note. The body is the fallback
 * for a note that has neither, so the row is never a bare title.
 */
private fun noteSubtitle(note: UserNote, citation: String?, unfiledLabel: String): String {
    val parts = listOfNotNull(
        note.folder.trim().ifEmpty { null } ?: unfiledLabel,
        citation,
    )
    return parts.joinToString(separator = " · ").ifBlank { note.body }
}

@Composable
private fun UserNoteRow(
    note: UserNote,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCard: (() -> Unit)? = null,
) {
    DeckWatchListRow(
        title = note.title.ifBlank { note.body },
        modifier = modifier,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenCard != null) {
                    IconButton(onClick = onOpenCard, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.notes_my_open_card),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTargetMin)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.notes_action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/**
 * Create or edit. A note is a title, a body and a free-text folder name; Save turns on only once
 * the title is there, so no note lands in the list without something to recognise it by.
 */
@Composable
private fun NoteEditorDialog(
    note: UserNote?,
    folderSuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, folder: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by rememberSaveable(note?.id) { mutableStateOf(note?.body.orEmpty()) }
    var folder by rememberSaveable(note?.id) { mutableStateOf(note?.folder.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                stringResource(
                    if (note == null) R.string.notes_my_new else R.string.notes_my_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_my_field_title)) },
                    isError = title.isBlank(),
                    supportingText = {
                        if (title.isBlank()) {
                            Text(stringResource(R.string.notes_my_title_required))
                        }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_my_field_body)) },
                    minLines = EditorBodyMinLines,
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.notes_my_field_folder)) },
                    singleLine = true,
                )
                if (folderSuggestions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingS)) {
                        folderSuggestions.take(MaxFolderSuggestions).forEach { suggestion ->
                            NotesFilterChip(
                                text = suggestion,
                                selected = folder.trim() == suggestion,
                                onClick = { folder = suggestion },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body, folder) },
                enabled = title.isNotBlank(),
                modifier = Modifier.heightIn(min = Dimens.TouchTargetMin),
            ) {
                Text(stringResource(R.string.notes_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Text(stringResource(R.string.notes_action_cancel))
            }
        },
    )
}

private val ListBottomInset = 88.dp
private const val EditorBodyMinLines = 4
private const val MaxFolderSuggestions = 4
