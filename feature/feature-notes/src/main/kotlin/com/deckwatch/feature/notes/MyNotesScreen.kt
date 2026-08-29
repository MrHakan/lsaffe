package com.deckwatch.feature.notes

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.core.model.UserNote

/**
 * MY NOTES — the officer's own notes, grouped into folders, each optionally attached to a
 * regulation card (shown as the citation chip) — §8.1.
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

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isEmpty) {
            NotesEmptyState(text = stringResource(R.string.notes_my_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.SpacingM,
                    end = Dimens.SpacingM,
                    top = Dimens.SpacingM,
                    bottom = ListBottomInset,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingS),
            ) {
                state.folders.forEach { folder ->
                    item(key = "folder-${folder.name}") {
                        ListSectionHeading(
                            text = folder.name.ifEmpty {
                                stringResource(R.string.notes_my_folder_unfiled)
                            },
                        )
                    }
                    items(items = folder.notes, key = { it.id }) { note ->
                        UserNoteRow(
                            note = note,
                            citation = state.citationFor(note),
                            onClick = { editing = note },
                            onCitationClick = { note.regulationRefKey?.let(onCardClick) },
                            onDelete = { pendingDelete = note },
                        )
                    }
                }
            }
        }

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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.notes_my_delete_title)) },
            text = { Text(stringResource(R.string.notes_my_delete_body, note.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(note.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.notes_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.notes_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun UserNoteRow(
    note: UserNote,
    citation: String?,
    onClick: () -> Unit,
    onCitationClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowCompact)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Dimens.SpacingM,
                top = Dimens.SpacingS,
                bottom = Dimens.SpacingS,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.body.isNotBlank()) {
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (citation != null) {
                    MetaChip(
                        text = citation,
                        monospace = true,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(top = Dimens.SpacingXs)
                            .clickable(onClick = onCitationClick),
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
    }
}

/** Create or edit. A note is just a title, a body and a free-text folder name. */
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
                            MetaChip(
                                text = suggestion,
                                modifier = Modifier.clickable { folder = suggestion },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body, folder) },
                enabled = title.isNotBlank() || body.isNotBlank(),
            ) {
                Text(stringResource(R.string.notes_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notes_action_cancel))
            }
        },
    )
}

private val ListBottomInset = 88.dp
private const val EditorBodyMinLines = 4
private const val MaxFolderSuggestions = 4
