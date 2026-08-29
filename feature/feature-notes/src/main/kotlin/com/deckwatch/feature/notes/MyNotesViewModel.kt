package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.UserNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One folder of the user's notes. [name] is blank for the unfiled group, which sorts first. */
data class NoteFolder(
    val name: String,
    val notes: List<UserNote>,
) {
    val isUnfiled: Boolean get() = name.isEmpty()
}

data class MyNotesUiState(
    val folders: List<NoteFolder> = emptyList(),
    /** regulation refKey -> citation, for the "attached to" chip on a note — §8.1. */
    val citations: Map<String, String> = emptyMap(),
) {
    val noteCount: Int get() = folders.sumOf { it.notes.size }
    val isEmpty: Boolean get() = noteCount == 0

    fun citationFor(note: UserNote): String? = note.regulationRefKey?.let { citations[it] ?: it }

    /** Existing folder names, offered as suggestions so folders stay consistent. */
    val folderNames: List<String> get() = folders.map { it.name }.filter { it.isNotEmpty() }
}

@HiltViewModel
class MyNotesViewModel @Inject constructor(
    private val reference: ReferenceRepository,
) : ViewModel() {

    val uiState: StateFlow<MyNotesUiState> = combine(
        reference.observeUserNotes(),
        reference.observeRegulationCards(),
    ) { notes, cards ->
        MyNotesUiState(
            folders = notes.groupBy { it.folder.trim() }
                .map { (folder, inFolder) ->
                    NoteFolder(folder, inFolder.sortedByDescending { it.updatedAt })
                }
                .sortedWith(compareBy({ it.name.isNotEmpty() }, { it.name.lowercase() })),
            citations = cards.associate { it.refKey to it.citation },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionTimeoutMillis),
        initialValue = MyNotesUiState(),
    )

    fun createNote(
        title: String,
        body: String,
        folder: String = "",
        regulationRefKey: String? = null,
    ) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            val now = Dates.nowMillis()
            reference.upsertUserNote(
                UserNote(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    body = body.trim(),
                    folder = folder.trim(),
                    regulationRefKey = regulationRefKey,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Edit in place: id, creation time and any card attachment are preserved. */
    fun updateNote(note: UserNote, title: String, body: String, folder: String) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            reference.upsertUserNote(
                note.copy(
                    title = title.trim(),
                    body = body.trim(),
                    folder = folder.trim(),
                    updatedAt = Dates.nowMillis(),
                ),
            )
        }
    }

    fun toggleFavourite(note: UserNote) {
        viewModelScope.launch {
            reference.upsertUserNote(
                note.copy(isFavourite = !note.isFavourite, updatedAt = Dates.nowMillis()),
            )
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch { reference.deleteUserNote(id) }
    }

    private companion object {
        const val SubscriptionTimeoutMillis = 5_000L
    }
}
