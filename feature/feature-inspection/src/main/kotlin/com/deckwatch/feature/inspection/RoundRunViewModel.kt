package com.deckwatch.feature.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the round being walked — §6.7. */
data class RoundRunItem(
    val itemId: String,
    val equipmentId: String,
    val tag: String,
    val symbolKey: String,
    val name: String?,
    val location: String?,
    val deckShortName: String,
    val condition: ConditionGrade? = null,
    val remark: String? = null,
) {
    val checked: Boolean get() = condition != null
}

data class RoundRunUiState(
    val loading: Boolean = true,
    val roundId: String? = null,
    val title: String = "",
    val performedBy: String = "",
    val items: List<RoundRunItem> = emptyList(),
    val doneCount: Int = 0,
    val deficiencyCount: Int = 0,
    val completedAt: Long? = null,
) {
    val itemCount: Int get() = items.size
    val finished: Boolean get() = completedAt != null
    val allChecked: Boolean get() = items.isNotEmpty() && items.all { it.checked }
}

/**
 * Walking one round item by item — the list-mode sweep of §7.1 C and §7.3.
 *
 * Grading writes both records the spec asks for: the [RoundItem]'s own condition (§6.7) *and* the
 * equipment's current condition through [EquipmentRepository.setCondition] (§7.3 step 1), so a round
 * leaves the register graded, not just the round sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoundRunViewModel(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val clock: () -> Long = Dates::nowMillis,
) : ViewModel() {

    @Inject
    constructor(
        vesselRepository: VesselRepository,
        equipmentRepository: EquipmentRepository,
        inspectionRepository: InspectionRepository,
    ) : this(
        vesselRepository = vesselRepository,
        equipmentRepository = equipmentRepository,
        inspectionRepository = inspectionRepository,
        clock = Dates::nowMillis,
    )

    private val roundIdState = MutableStateFlow<String?>(null)
    private val itemCache = MutableStateFlow<Map<String, RoundItem>>(emptyMap())
    private val roundCache = MutableStateFlow<Round?>(null)

    /** Point the screen at a round. Idempotent, so it is safe from a `LaunchedEffect`. */
    fun bind(roundId: String) {
        roundIdState.value = roundId
    }

    private val vesselFlow = vesselRepository.observeActiveVessel()

    private val roundsFlow: Flow<List<Round>> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) flowOf(emptyList()) else inspectionRepository.observeRounds(vessel.id)
    }

    private val itemsFlow: Flow<List<RoundItem>> = roundIdState.flatMapLatest { roundId ->
        if (roundId == null) flowOf(emptyList()) else inspectionRepository.observeRoundItems(roundId)
    }

    private val equipmentFlow: Flow<List<Equipment>> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) flowOf(emptyList()) else equipmentRepository.observeEquipment(vessel.id)
    }

    private val decksFlow: Flow<List<Deck>> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) flowOf(emptyList()) else vesselRepository.observeDecks(vessel.id)
    }

    val uiState: StateFlow<RoundRunUiState> = combine(
        roundIdState,
        roundsFlow,
        itemsFlow,
        equipmentFlow,
        decksFlow,
    ) { roundId, rounds, items, equipment, decks ->
        val round = rounds.firstOrNull { it.id == roundId }
        roundCache.value = round
        itemCache.value = items.associateBy { it.id }
        val equipmentById = equipment.associateBy { it.id }
        val decksById = decks.associateBy { it.id }
        RoundRunUiState(
            loading = false,
            roundId = roundId,
            title = round?.title.orEmpty(),
            performedBy = round?.performedBy.orEmpty(),
            items = items.mapNotNull { item ->
                val gear = equipmentById[item.equipmentId] ?: return@mapNotNull null
                RoundRunItem(
                    itemId = item.id,
                    equipmentId = gear.id,
                    tag = gear.tag,
                    symbolKey = gear.symbolKey,
                    name = gear.name,
                    location = gear.location,
                    deckShortName = gear.deckId?.let(decksById::get).shortName(),
                    condition = item.condition,
                    remark = item.remark,
                )
            },
            doneCount = items.count { it.condition != null },
            deficiencyCount = items.count { entry -> entry.condition?.let { it.score <= DEFICIENT_SCORE } == true },
            completedAt = round?.completedAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = RoundRunUiState(),
    )

    /**
     * Grade one item — §7.3. Writes the round item, carries the grade onto the equipment record and
     * re-counts the round so the history list is accurate while the round is still running.
     */
    fun grade(itemId: String, grade: ConditionGrade) {
        val item = itemCache.value[itemId] ?: return
        viewModelScope.launch {
            val now = clock()
            val updated = item.copy(checkedAt = now, condition = grade)
            inspectionRepository.upsertRoundItem(updated)
            equipmentRepository.setCondition(item.equipmentId, grade, now)
            recount(updated)
        }
    }

    /** A free-text remark on one item; does not by itself count the item as checked. */
    fun setRemark(itemId: String, remark: String) {
        val item = itemCache.value[itemId] ?: return
        viewModelScope.launch {
            inspectionRepository.upsertRoundItem(item.copy(remark = remark.ifBlank { null }))
        }
    }

    /**
     * Finish the round — §6.7 `completedAt` plus the final counts. Skipped items stay ungraded, so
     * "18 of 24 checked" survives into the history and the exported round report (§13.3).
     */
    fun finish() {
        val round = roundCache.value ?: return
        viewModelScope.launch {
            val items = itemCache.value.values.toList()
            inspectionRepository.upsertRound(
                RoundMaterialiser.recount(round, items).copy(completedAt = clock()),
            )
        }
    }

    private suspend fun recount(updated: RoundItem) {
        val round = roundCache.value ?: return
        val items = itemCache.value.values.map { if (it.id == updated.id) updated else it }
        inspectionRepository.upsertRound(RoundMaterialiser.recount(round, items))
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val DEFICIENT_SCORE = 1
    }
}
