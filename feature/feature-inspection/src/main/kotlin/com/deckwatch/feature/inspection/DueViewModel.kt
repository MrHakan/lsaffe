package com.deckwatch.feature.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Due tab — a work list, not a dashboard (§12).
 *
 * Joins the active vessel's open task instances (§6.6) with its equipment, decks, zones, logical
 * categories and the bundled task-definition and type catalogues, buckets them into the five
 * segments of §12 and applies the combinable filter set.
 *
 * @param today the epoch-day the buckets are computed against. Injected as a lambda so every
 *   boundary in [DueBucketing.segmentOf] is testable against a fixed day rather than the wall clock.
 * @param clock epoch-millis for `updatedAt` stamps on writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DueViewModel(
    private val vesselRepository: VesselRepository,
    private val equipmentRepository: EquipmentRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val referenceRepository: ReferenceRepository,
    private val today: () -> Long = Dates::todayEpochDay,
    private val clock: () -> Long = Dates::nowMillis,
) : ViewModel() {

    /** The graph's entry point; the clocks are ambient state, not dependencies to be bound. */
    @Inject
    constructor(
        vesselRepository: VesselRepository,
        equipmentRepository: EquipmentRepository,
        maintenanceRepository: MaintenanceRepository,
        referenceRepository: ReferenceRepository,
    ) : this(
        vesselRepository = vesselRepository,
        equipmentRepository = equipmentRepository,
        maintenanceRepository = maintenanceRepository,
        referenceRepository = referenceRepository,
        today = Dates::todayEpochDay,
        clock = Dates::nowMillis,
    )

    private val segmentState = MutableStateFlow(DueSegment.OVERDUE)
    private val filterState = MutableStateFlow(DueFilters())
    private val surveyPrepState = MutableStateFlow(false)

    /** Locale of the host composable, so an exported payload carries the officer's language — C8. */
    private val turkishState = MutableStateFlow(false)

    /**
     * The raw instances behind the current rows, so a defer can write the occurrence back without a
     * second read. [DueRow] is a projection for the UI; the repository needs the whole record.
     */
    private val instanceCache = MutableStateFlow<Map<String, TaskInstance>>(emptyMap())

    private val vesselFlow: Flow<Vessel?> = vesselRepository.observeActiveVessel()

    private val contextFlow: Flow<VesselContext> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) {
            flowOf(VesselContext())
        } else {
            combine(
                vesselRepository.observeDecks(vessel.id),
                vesselRepository.observeCategories(vessel.id),
            ) { decks, categories -> decks to categories }
                .flatMapLatest { (decks, categories) ->
                    zonesOf(decks).map { zones -> VesselContext(vessel, decks, zones, categories) }
                }
        }
    }

    private val workFlow: Flow<WorkData> = vesselFlow.flatMapLatest { vessel ->
        if (vessel == null) {
            flowOf(WorkData())
        } else {
            combine(
                equipmentRepository.observeEquipment(vessel.id),
                maintenanceRepository.observeOpenInstancesForVessel(vessel.id),
                maintenanceRepository.observeTaskDefinitions(),
                referenceRepository.observeEquipmentTypes(),
            ) { equipment, instances, definitions, types ->
                WorkData(equipment, instances, definitions, types)
            }
        }
    }

    /**
     * equipment id -> category ids, subscribed **only while a category filter is set** (§6.4 keeps
     * the cross-reference per equipment item, so an unconditional join would open one flow per
     * marker on a 600-item vessel).
     */
    private val membershipFlow: Flow<Map<String, Set<String>>> = combine(
        filterState.map { it.categoryId }.distinctUntilChanged(),
        workFlow.map { work -> work.equipment.map(Equipment::id) }.distinctUntilChanged(),
    ) { categoryId, ids -> categoryId to ids }
        .flatMapLatest { (categoryId, ids) ->
            if (categoryId == null || ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    ids.map { id -> equipmentRepository.observeCategoryIds(id).map { id to it.toSet() } },
                ) { pairs -> pairs.toMap() }
            }
        }

    private val snapshotFlow: Flow<DueSnapshot> =
        combine(contextFlow, workFlow) { context, work -> buildSnapshot(context, work) }

    val uiState: StateFlow<DueUiState> = combine(
        snapshotFlow,
        filterState,
        segmentState,
        surveyPrepState,
        membershipFlow,
    ) { snapshot, filters, segment, surveyPrep, membership ->
        project(snapshot, filters, segment, surveyPrep, membership)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = DueUiState(),
    )

    // ------------------------------------------------------------------ user intent

    fun selectSegment(segment: DueSegment) {
        segmentState.value = segment
    }

    fun setDeckFilter(deckId: String?) = filterState.update { it.copy(deckId = deckId) }

    fun setZoneFilter(zoneId: String?) = filterState.update { it.copy(zoneId = zoneId) }

    fun setCategoryFilter(categoryId: String?) = filterState.update { it.copy(categoryId = categoryId) }

    fun setGroupFilter(group: EquipmentGroup?) = filterState.update { it.copy(group = group) }

    fun setPerformedByFilter(performedBy: PerformedBy?) = filterState.update { it.copy(performedBy = performedBy) }

    fun setConditionFilter(condition: ConditionGrade?) = filterState.update { it.copy(condition = condition) }

    fun clearFilters() {
        filterState.value = DueFilters()
    }

    fun setSurveyPrep(enabled: Boolean) {
        surveyPrepState.value = enabled
    }

    fun toggleSurveyPrep() {
        surveyPrepState.update { !it }
    }

    /**
     * Swipe-right "mark done" — §12. Writes the completion (§6.6 evidence fields), carries the
     * grade the officer recorded onto the equipment record (§7.3), then asks the due engine to
     * re-derive the next occurrence (§11.2 "on any write to … task completion").
     *
     * `recomputeDue` is called explicitly even though a repository implementation may already
     * recompute inside its completion transaction: it is idempotent, and the Due tab must never be
     * the reason a next occurrence failed to appear.
     */
    fun completeTask(input: TaskCompletionInput) {
        viewModelScope.launch {
            maintenanceRepository.completeTask(
                instanceId = input.instanceId,
                completedDate = input.completedDate,
                completedBy = input.completedBy?.ifBlank { null },
                serviceProvider = input.serviceProvider?.ifBlank { null },
                certificateNumber = input.certificateNumber?.ifBlank { null },
                findings = input.findings?.ifBlank { null },
                conditionAfter = input.conditionAfter,
            )
            input.conditionAfter?.let { grade ->
                equipmentRepository.setCondition(input.equipmentId, grade, clock())
            }
            maintenanceRepository.recomputeDue(input.equipmentId)
        }
    }

    /**
     * Swipe-left "defer with reason" — §12.
     *
     * **Why `SKIPPED`.** §6.6 gives a task instance no dedicated "deferred" state; of the six
     * [TaskStatus] members, `SKIPPED` is the one that means *this occurrence was consciously not
     * performed*, and `findings` is the free-text field that already travels with the occurrence
     * into the history and the exported report. So a deferral is written as
     * `status = SKIPPED, findings = <reason>` through [MaintenanceRepository.upsertInstances],
     * which keeps the occurrence's id, due date and window intact — the officer deferred the job,
     * they did not reschedule it.
     *
     * The due engine is deliberately **not** re-run: a deferral is an annotation on an existing
     * occurrence, not a completion, so the schedule is unchanged. Deferred rows leave the urgent
     * segments and collect in [DueSegment.PLANNED] (see [DueBucketing.segmentOf]) rather than
     * disappearing from the work list.
     */
    fun deferTask(instanceId: String, reason: String) {
        val instance = instanceCache.value[instanceId] ?: return
        viewModelScope.launch {
            maintenanceRepository.upsertInstances(
                listOf(
                    instance.copy(
                        status = TaskStatus.SKIPPED,
                        findings = reason.ifBlank { null },
                        updatedAt = clock(),
                    ),
                ),
            )
        }
    }

    /**
     * The current list as an export payload — §12, §13.3. In survey-prep mode the payload is the
     * whole "before next survey" workload (ship's staff then shore provider); otherwise it is the
     * rows of the selected segment, in the order they appear on screen.
     */
    fun buildExportRequest(): DueExportRequest {
        val state = uiState.value
        val prep = state.surveyPrep
        val rows = if (state.surveyPrepEnabled && prep != null) prep.shipStaff + prep.shoreProvider else state.rows
        val turkish = turkishState.value
        return DueExportRequest(
            vesselName = state.vesselName,
            vesselImoNumber = state.vesselImoNumber,
            segment = state.segment,
            generatedOnEpochDay = state.today.takeIf { it != 0L } ?: today(),
            filters = describeFilters(state),
            lines = rows.map { row ->
                DueExportLine(
                    tag = row.tag,
                    task = row.taskTitle.resolve(turkish),
                    dueDate = row.dueDate,
                    dayDelta = row.dayDelta,
                    performedBy = row.performedBy,
                    deck = row.deckShortName.takeIf { it.isNotBlank() },
                    status = row.status,
                    equipmentId = row.equipmentId,
                )
            },
            surveyCertExpiry = if (state.surveyPrepEnabled) prep?.certExpiry else null,
        )
    }

    /** Locale for the export payload's task titles; set by the composable — C8. */
    fun setTurkish(turkish: Boolean) {
        turkishState.value = turkish
    }

    // ------------------------------------------------------------------ projection

    private fun buildSnapshot(context: VesselContext, work: WorkData): DueSnapshot {
        val todayEpochDay = today()
        instanceCache.value = work.instances.associateBy { it.id }

        val decksById = context.decks.associateBy { it.id }
        val equipmentById = work.equipment.associateBy { it.id }
        val definitionsByKey = work.definitions.associateBy { it.key }
        val typesByKey = work.types.associateBy { it.typeKey }
        val certExpiry = context.vessel?.safetyEquipmentCertExpiry

        val rows = work.instances.mapNotNull { instance ->
            val equipment = equipmentById[instance.equipmentId] ?: return@mapNotNull null
            val definition = definitionsByKey[instance.taskKey]
            val type = typesByKey[equipment.typeKey]
            val deck = equipment.deckId?.let(decksById::get)
            DueRow(
                instanceId = instance.id,
                equipmentId = equipment.id,
                tag = equipment.tag,
                symbolKey = equipment.symbolKey,
                deckId = equipment.deckId,
                deckShortName = deck.shortName(),
                zoneId = equipment.zoneId,
                taskKey = instance.taskKey,
                taskTitle = definition?.title() ?: LocalisedText(instance.taskKey),
                equipmentTypeName = type?.let { LocalisedText(it.nameEn, it.nameTr) } ?: LocalisedText.Empty,
                dueDate = instance.dueDate,
                dayDelta = instance.dueDate - todayEpochDay,
                status = instance.status,
                performedBy = definition?.performedBy ?: PerformedBy.SHIP_STAFF,
                condition = equipment.condition,
                group = type?.group ?: EquipmentGroup.OTHER,
                segment = DueBucketing.segmentOf(instance, todayEpochDay, certExpiry),
            )
        }

        return DueSnapshot(
            vessel = context.vessel,
            today = todayEpochDay,
            rows = rows,
            options = DueFilterOptions(
                decks = context.decks.map { FilterOption(it.id, it.shortName()) },
                zones = context.zones.map { FilterOption(it.id, it.name) },
                categories = context.categories.map { FilterOption(it.id, it.name) },
                groups = rows.map { it.group }.distinct().sortedBy { it.ordinal },
                performers = rows.map { it.performedBy }.distinct().sortedBy { it.ordinal },
                conditions = rows.map { it.condition }.distinct().sortedByDescending { it.score },
            ),
        )
    }

    private fun project(
        snapshot: DueSnapshot,
        filters: DueFilters,
        segment: DueSegment,
        surveyPrepEnabled: Boolean,
        membership: Map<String, Set<String>>,
    ): DueUiState {
        val filtered = DueBucketing.sortForWorkList(
            DueBucketing.applyFilters(snapshot.rows, filters, membership),
        )
        val certExpiry = snapshot.vessel?.safetyEquipmentCertExpiry
        return DueUiState(
            loading = false,
            vesselName = snapshot.vessel?.name.orEmpty(),
            vesselImoNumber = snapshot.vessel?.imoNumber,
            certExpiry = certExpiry,
            today = snapshot.today,
            segment = segment,
            filters = filters,
            options = snapshot.options,
            counts = DueBucketing.countBySegment(filtered),
            rows = filtered.filter { it.segment == segment },
            surveyPrepEnabled = surveyPrepEnabled,
            surveyPrep = certExpiry
                ?.takeIf { surveyPrepEnabled }
                ?.let { DueBucketing.surveyPrep(filtered, it, snapshot.today) },
        )
    }

    private fun describeFilters(state: DueUiState): DueExportFilters = DueExportFilters(
        deckName = state.options.decks.labelOf(state.filters.deckId),
        zoneName = state.options.zones.labelOf(state.filters.zoneId),
        categoryName = state.options.categories.labelOf(state.filters.categoryId),
        group = state.filters.group,
        performedBy = state.filters.performedBy,
        condition = state.filters.condition,
    )

    private fun zonesOf(decks: List<Deck>): Flow<List<Zone>> =
        if (decks.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(decks.map { vesselRepository.observeZones(it.id) }) { zones -> zones.toList().flatten() }
        }

    private data class VesselContext(
        val vessel: Vessel? = null,
        val decks: List<Deck> = emptyList(),
        val zones: List<Zone> = emptyList(),
        val categories: List<Category> = emptyList(),
    )

    private data class WorkData(
        val equipment: List<Equipment> = emptyList(),
        val instances: List<TaskInstance> = emptyList(),
        val definitions: List<TaskDefinition> = emptyList(),
        val types: List<EquipmentType> = emptyList(),
    )

    private data class DueSnapshot(
        val vessel: Vessel? = null,
        val today: Long = 0L,
        val rows: List<DueRow> = emptyList(),
        val options: DueFilterOptions = DueFilterOptions(),
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

/** Everything the completion dialog collects — §6.6 evidence fields. */
data class TaskCompletionInput(
    val instanceId: String,
    val equipmentId: String,
    /** Epoch-days; defaults to today in the dialog. */
    val completedDate: Long,
    val completedBy: String? = null,
    val serviceProvider: String? = null,
    val certificateNumber: String? = null,
    val findings: String? = null,
    val conditionAfter: ConditionGrade? = null,
)

/** The spine label of a deck: its short code when it has one, its name otherwise — §6.2. */
internal fun Deck?.shortName(): String =
    this?.let { deck -> deck.shortCode?.takeIf { it.isNotBlank() } ?: deck.name }.orEmpty()

internal fun TaskDefinition.title(): LocalisedText = LocalisedText(titleEn, titleTr)

private fun List<FilterOption>.labelOf(id: String?): String? =
    id?.let { wanted -> firstOrNull { it.id == wanted }?.label }
