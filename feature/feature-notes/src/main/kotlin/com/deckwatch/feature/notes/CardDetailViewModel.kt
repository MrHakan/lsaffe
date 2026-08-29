package com.deckwatch.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.UserNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** The full card plus everything the officer has written on it — §8.2, §8.4. */
data class CardDetailUiState(
    val card: RegulationCard? = null,
    val appliesToNames: List<String> = emptyList(),
    val myNotes: List<UserNote> = emptyList(),
) {
    val isLoaded: Boolean get() = card != null
}

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val reference: ReferenceRepository,
) : ViewModel() {

    private val refKey = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CardDetailUiState> = combine(
        refKey,
        reference.observeRegulationCards(),
        reference.observeEquipmentTypes(),
        reference.observeUserNotes(),
    ) { key, cards, types, notes ->
        val card = cards.firstOrNull { it.refKey == key }
        CardDetailUiState(
            card = card,
            appliesToNames = card?.appliesToTypeKeys.orEmpty().map { typeKey ->
                types.firstOrNull { it.typeKey == typeKey }?.nameEn ?: typeKey
            },
            myNotes = notes.filter { it.regulationRefKey != null && it.regulationRefKey == key },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SubscriptionTimeoutMillis),
        initialValue = CardDetailUiState(),
    )

    fun open(key: String) {
        refKey.value = key
    }

    /**
     * "Add my note" from the card footer — §8.2. The note is attached to the card by `refKey`
     * so it comes back both here and in MY NOTES with the citation chip.
     */
    fun addNote(title: String, body: String, folder: String = "") {
        val key = refKey.value ?: return
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch {
            val now = Dates.nowMillis()
            reference.upsertUserNote(
                UserNote(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    body = body.trim(),
                    folder = folder.trim(),
                    regulationRefKey = key,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private companion object {
        const val SubscriptionTimeoutMillis = 5_000L
    }
}
