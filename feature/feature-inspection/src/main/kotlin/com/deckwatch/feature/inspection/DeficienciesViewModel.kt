package com.deckwatch.feature.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One deficiency with the tag of the equipment it was raised against, if any — §6.8. */
data class DeficiencyRow(
    val deficiency: Deficiency,
    val equipmentTag: String? = null,
    val symbolKey: String? = null,
)

data class DeficienciesUiState(
    val loading: Boolean = true,
    val hasVessel: Boolean = false,
    val open: List<DeficiencyRow> = emptyList(),
    val closed: List<DeficiencyRow> = emptyList(),
    /** Equipment the officer can attach a new deficiency to; id + tag. */
    val equipmentOptions: List<FilterOption> = emptyList(),
    /** Epoch-day used to date a new deficiency or a closure. */
    val today: Long = 0L,
)

/** The fields the raise / edit forms collect — §6.8. */
data class DeficiencyDraft(
    val id: String? = null,
    val equipmentId: String? = null,
    val severity: Severity = Severity.MINOR,
    val title: String = "",
    val description: String = "",
    val correctiveAction: String? = null,
    /** Epoch-days. */
    val targetDate: Long? = null,
    val raisedBy: String = "",
    val status: DeficiencyStatus = DeficiencyStatus.OPEN,
    val sparePartRequired: String? = null,
)

/**
 * Open and closed deficiencies for the active vessel — §6.8.
 *
 * Sorted worst-first: a `CRITICAL_DETAINABLE` finding is the one that stops the ship, so it is
 * never below an observation, whatever the dates say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeficienciesViewModel(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val today: () -> Long = Dates::todayEpochDay,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
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
        today = Dates::todayEpochDay,
        idFactory = { UUID.randomUUID().toString() },
    )

    private val vesselFlow = vesselRepository.observeActiveVessel()

    private val deficienciesFlow: Flow<List<Deficiency>> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) flowOf(emptyList()) else inspectionRepository.observeDeficiencies(vessel.id)
    }

    private val equipmentFlow = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) flowOf(emptyList()) else equipmentRepository.observeEquipment(vessel.id)
    }

    /** The vessel a new deficiency belongs to — §6.8 requires one. */
    private var activeVesselId: String? = null

    val uiState: StateFlow<DeficienciesUiState> =
        combine(vesselFlow, deficienciesFlow, equipmentFlow) { vessel, deficiencies, equipment ->
            activeVesselId = vessel?.id
            val equipmentById = equipment.associateBy { it.id }
            val rows = deficiencies
                .sortedWith(
                    compareByDescending<Deficiency> { it.severity.ordinal }
                        .thenBy { it.raisedDate }
                        .thenBy { it.id },
                )
                .map { deficiency ->
                    val gear = deficiency.equipmentId?.let(equipmentById::get)
                    DeficiencyRow(deficiency, gear?.tag, gear?.symbolKey)
                }
            DeficienciesUiState(
                loading = false,
                hasVessel = vessel != null,
                open = rows.filter { it.deficiency.status != DeficiencyStatus.CLOSED },
                closed = rows.filter { it.deficiency.status == DeficiencyStatus.CLOSED },
                equipmentOptions = equipment.map { FilterOption(it.id, it.tag) },
                today = today(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = DeficienciesUiState(),
        )

    /** Raise a new deficiency against the active vessel — §6.8, §7.3 step 4. */
    fun raise(draft: DeficiencyDraft) {
        val vesselId = activeVesselId ?: return
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            inspectionRepository.upsertDeficiency(
                Deficiency(
                    id = idFactory(),
                    vesselId = vesselId,
                    equipmentId = draft.equipmentId,
                    raisedDate = today(),
                    raisedBy = draft.raisedBy,
                    severity = draft.severity,
                    title = draft.title,
                    description = draft.description,
                    correctiveAction = draft.correctiveAction?.ifBlank { null },
                    targetDate = draft.targetDate,
                    status = draft.status,
                    sparePartRequired = draft.sparePartRequired?.ifBlank { null },
                ),
            )
        }
    }

    /** Edit an existing deficiency: corrective action, target date, severity, status, spare part. */
    fun update(existing: Deficiency, draft: DeficiencyDraft) {
        viewModelScope.launch {
            inspectionRepository.upsertDeficiency(
                existing.copy(
                    equipmentId = draft.equipmentId,
                    severity = draft.severity,
                    title = draft.title.ifBlank { existing.title },
                    description = draft.description,
                    correctiveAction = draft.correctiveAction?.ifBlank { null },
                    targetDate = draft.targetDate,
                    status = draft.status,
                    sparePartRequired = draft.sparePartRequired?.ifBlank { null },
                ),
            )
        }
    }

    /** Close a deficiency — §6.8 `closedDate` / `closedBy`. */
    fun close(existing: Deficiency, closedBy: String, closedDate: Long = today()) {
        viewModelScope.launch {
            inspectionRepository.upsertDeficiency(
                existing.copy(
                    status = DeficiencyStatus.CLOSED,
                    closedDate = closedDate,
                    closedBy = closedBy.ifBlank { null },
                ),
            )
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
