package com.deckwatch.feature.vessel.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoryManagerUiState(
    val vessel: Vessel? = null,
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && categories.isEmpty()
}

/** Fields the category dialog collects. */
data class CategoryDraft(
    val id: String? = null,
    val name: String,
    val colorArgb: Int,
    val isGlobal: Boolean,
)

/**
 * Logical categories (§6.4) — the "what" axis: a tag applied to equipment regardless of where it
 * sits. A category with a null `vesselId` is global and shows on every vessel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryManagerViewModel @Inject constructor(
    private val vesselRepository: VesselRepository,
) : ViewModel() {

    private val requestedVesselId = MutableStateFlow<String?>(null)
    private val pendingDelete = MutableStateFlow<String?>(null)

    val deleteTarget: StateFlow<String?> = pendingDelete

    private val resolvedVessel: Flow<Vessel?> = combine(
        requestedVesselId,
        vesselRepository.observeVessels(),
        vesselRepository.observeActiveVessel(),
    ) { requested, all, active ->
        if (requested == null) active else all.firstOrNull { it.id == requested }
    }

    val uiState: StateFlow<CategoryManagerUiState> = resolvedVessel
        .flatMapLatest { vessel ->
            vesselRepository.observeCategories(vessel?.id).map { categories ->
                CategoryManagerUiState(vessel = vessel, categories = categories, isLoading = false)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CategoryManagerUiState(),
        )

    /** `null` resolves to the active vessel (§5). */
    fun bind(vesselId: String?) {
        requestedVesselId.value = vesselId
    }

    /**
     * Writes the category. Flipping the global toggle rewrites `vesselId` in place — the id stays
     * the same, so every `equipment_category_xref` row pointing at it survives the change.
     */
    fun save(draft: CategoryDraft) {
        viewModelScope.launch {
            val vesselId = uiState.value.vessel?.id
            if (!draft.isGlobal && vesselId == null) return@launch
            val existing = draft.id?.let { id -> uiState.value.categories.firstOrNull { it.id == id } }
            vesselRepository.upsertCategory(
                Category(
                    id = draft.id ?: UUID.randomUUID().toString(),
                    vesselId = if (draft.isGlobal) null else vesselId,
                    name = draft.name.trim(),
                    colorArgb = draft.colorArgb,
                    iconKey = existing?.iconKey,
                    sortOrder = existing?.sortOrder ?: nextSortOrder(vesselId),
                ),
            )
        }
    }

    fun askDelete(categoryId: String) {
        pendingDelete.value = categoryId
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete(categoryId: String) {
        pendingDelete.value = null
        viewModelScope.launch { vesselRepository.deleteCategory(categoryId) }
    }

    private suspend fun nextSortOrder(vesselId: String?): Int =
        (vesselRepository.observeCategories(vesselId).first().maxOfOrNull { it.sortOrder } ?: -1) + 1

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
