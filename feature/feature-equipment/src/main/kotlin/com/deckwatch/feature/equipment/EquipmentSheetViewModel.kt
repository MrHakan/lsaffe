package com.deckwatch.feature.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.reminders.ItemReminders
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.Zone
import com.deckwatch.feature.equipment.attributes.AttributeCodec
import com.deckwatch.feature.equipment.attributes.AttributeDraft
import com.deckwatch.feature.equipment.attributes.AttributeError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The attribute editor while it is open — values plus whatever failed validation. */
internal data class AttributeEditState(
    val values: AttributeDraft,
    val errors: Map<String, AttributeError> = emptyMap(),
)

/** Everything the equipment sheet and the full-screen detail render — §7.4. */
internal data class EquipmentSheetUiState(
    val loading: Boolean = true,
    /** The record was deleted or never existed: the host should dismiss. */
    val missing: Boolean = false,
    val equipment: Equipment? = null,
    val type: EquipmentType? = null,
    /** Stored attribute values as raw editor text — §9.3. */
    val attributeValues: AttributeDraft = emptyMap(),
    /** Non-null while the officer is editing the attributes. */
    val editor: AttributeEditState? = null,
    val checklist: List<ChecklistItemUi> = emptyList(),
    val checklistComplete: Boolean = false,
    /** The task key a completed checklist closes — see [MonthlyChecklist]. */
    val monthlyTaskKey: String? = null,
    val tasks: List<TaskRowUi> = emptyList(),
    val openDeficiencies: List<Deficiency> = emptyList(),
    val requirements: List<RegulationCard> = emptyList(),
    /** Epoch-day of the most recent completed task — the "last inspection" line of §7.4 half. */
    val lastInspection: Long? = null,
    val deficiencyDraft: DeficiencyDraft? = null,
    val conditionUndo: ConditionUndo? = null,
    val message: SheetMessage? = null,
    val todayEpochDay: Long = Dates.todayEpochDay(),
)

/**
 * State and writes for the equipment bottom sheet and the full-screen detail — §7.3, §7.4, §9.3.
 *
 * Repository interfaces only: this module never sees Room. The due engine is never run here —
 * recomputing and persisting the schedule is
 * [MaintenanceRepository.recomputeDue]'s job (§11.2), which this view model calls after **every**
 * attribute write.
 */
@HiltViewModel
internal class EquipmentSheetViewModel @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val vesselRepository: VesselRepository,
    private val referenceRepository: ReferenceRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val inspectionRepository: InspectionRepository,
    private val itemReminders: ItemReminders,
) : ViewModel() {

    private val boundId = MutableStateFlow<String?>(null)
    private val moveDeckId = MutableStateFlow<String?>(null)
    private val editor = MutableStateFlow<AttributeEditState?>(null)
    private val deficiencyDraft = MutableStateFlow<DeficiencyDraft?>(null)
    private val conditionUndo = MutableStateFlow<ConditionUndo?>(null)
    private val message = MutableStateFlow<SheetMessage?>(null)
    private var undoTimer: Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val content: Flow<LoadedContent> = boundId.flatMapLatest { id ->
        if (id == null) flowOf(LoadedContent.idle()) else observeContent(id)
    }

    /**
     * Started eagerly and kept hot for the view model's life: the write actions read
     * `uiState.value` to find the record they act on, so the state must be current even in the
     * instant between the sheet opening and Compose subscribing.
     */
    val uiState: StateFlow<EquipmentSheetUiState> =
        combine(content, editor, deficiencyDraft, conditionUndo, message) { loaded, edit, draft, undo, note ->
            loaded.state.copy(
                editor = edit,
                deficiencyDraft = draft,
                conditionUndo = undo,
                message = note,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EquipmentSheetUiState())

    /**
     * The vessel's decks, for the move picker — §6.5. Equipment created from the tab's FAB lands
     * unplaced, and this is how it gets onto a deck without the 2.5D canvas (§7.1 A) existing yet.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val decks: StateFlow<List<Deck>> = uiState
        .map { it.equipment?.vesselId }
        .distinctUntilChanged()
        .flatMapLatest { vesselId ->
            if (vesselId == null) flowOf(emptyList()) else vesselRepository.observeDecks(vesselId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(DECKS_STOP_TIMEOUT_MILLIS), emptyList())

    /**
     * Zones of the deck the move picker is currently showing — §6.4. Held here rather than in the
     * dialog because a zone belongs to a deck, so the list has to follow the deck being picked.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val zonesForMove: StateFlow<List<Zone>> = moveDeckId
        .flatMapLatest { deckId ->
            if (deckId == null) flowOf(emptyList()) else vesselRepository.observeZones(deckId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(DECKS_STOP_TIMEOUT_MILLIS), emptyList())

    /** Show the zones of [deckId] in the move picker; null clears the second step. */
    fun selectDeckForMove(deckId: String?) {
        moveDeckId.value = deckId
    }

    /** Point the sheet at an equipment record; re-binding to the same id is a no-op. */
    fun bind(equipmentId: String) {
        if (boundId.value == equipmentId) return
        boundId.value = equipmentId
        editor.value = null
        deficiencyDraft.value = null
        conditionUndo.value = null
        message.value = null
        undoTimer?.cancel()
    }

    // ------------------------------------------------------------------ §7.3 quick action

    /**
     * Write a condition grade — §7.3 steps 1–4.
     *
     * The write happens first and unconditionally: `condition` and `conditionSetAt` are stamped
     * immediately. The undo affordance opens for [UNDO_WINDOW_MILLIS] (C10), and a grade of
     * `DEFECTIVE` or `OUT_OF_SERVICE` expands the pre-filled deficiency form — which the officer may
     * ignore entirely.
     *
     * @param onWritten sweep-mode hook, invoked **after** the write lands so the host can advance to
     *   the next unchecked item (§7.3).
     */
    fun setCondition(grade: ConditionGrade, onWritten: (String, ConditionGrade) -> Unit = { _, _ -> }) {
        val current = uiState.value.equipment ?: return
        val type = uiState.value.type
        val now = Dates.nowMillis()
        viewModelScope.launch {
            equipmentRepository.setCondition(current.id, grade, now)
            conditionUndo.value = ConditionUndo(
                equipmentId = current.id,
                previousGrade = current.condition,
                previousSetAt = current.conditionSetAt,
                newGrade = grade,
            )
            deficiencyDraft.value = DeficiencyDraft.prefill(
                equipment = current,
                type = type,
                grade = grade,
                todayEpochDay = Dates.todayEpochDay(),
            )
            onWritten(current.id, grade)
            armUndoTimer()
        }
    }

    /** Put the previous grade back, exactly as it was including its original timestamp. */
    fun undoCondition() {
        val undo = conditionUndo.value ?: return
        undoTimer?.cancel()
        conditionUndo.value = null
        deficiencyDraft.value = null
        viewModelScope.launch {
            val current = equipmentRepository.getEquipment(undo.equipmentId) ?: return@launch
            equipmentRepository.upsertEquipment(
                current.copy(
                    condition = undo.previousGrade,
                    conditionSetAt = undo.previousSetAt,
                    updatedAt = Dates.nowMillis(),
                ),
            )
        }
    }

    private fun armUndoTimer() {
        undoTimer?.cancel()
        undoTimer = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            conditionUndo.value = null
        }
    }

    // ------------------------------------------------------------------ §7.3 deficiency form

    fun updateDeficiencyTitle(value: String) = updateDraft { it.copy(title = value) }

    fun updateDeficiencyDescription(value: String) = updateDraft { it.copy(description = value) }

    fun updateDeficiencyRaisedBy(value: String) = updateDraft { it.copy(raisedBy = value) }

    fun updateDeficiencySeverity(value: Severity) = updateDraft { it.copy(severity = value) }

    /** Dismiss without saving. The grade stays written — the form is never forced (§7.3). */
    fun dismissDeficiency() {
        deficiencyDraft.value = null
    }

    fun saveDeficiency() {
        val draft = deficiencyDraft.value ?: return
        deficiencyDraft.value = null
        viewModelScope.launch {
            inspectionRepository.upsertDeficiency(
                Deficiency(
                    id = UUID.randomUUID().toString(),
                    vesselId = draft.vesselId,
                    equipmentId = draft.equipmentId,
                    raisedDate = draft.raisedDate,
                    raisedBy = draft.raisedBy,
                    severity = draft.severity,
                    title = draft.title,
                    description = draft.description,
                    status = DeficiencyStatus.OPEN,
                ),
            )
            message.value = SheetMessage.DEFICIENCY_SAVED
        }
    }

    private fun updateDraft(transform: (DeficiencyDraft) -> DeficiencyDraft) {
        deficiencyDraft.value = deficiencyDraft.value?.let(transform)
    }

    // ------------------------------------------------------------------ §9.3 attributes

    fun startEditingAttributes() {
        editor.value = AttributeEditState(uiState.value.attributeValues)
    }

    fun cancelEditingAttributes() {
        editor.value = null
    }

    fun updateAttribute(key: String, raw: String) {
        val current = editor.value ?: return
        editor.value = current.copy(values = current.values + (key to raw), errors = current.errors - key)
    }

    /**
     * Validate and persist the attribute draft, then recompute the schedule.
     *
     * §9.3: `affectsTasks` values re-derive the task set, so **every** attribute write is followed by
     * [MaintenanceRepository.recomputeDue].
     */
    fun saveAttributes() {
        val state = uiState.value
        val equipment = state.equipment ?: return
        val type = state.type ?: return
        val edit = editor.value ?: return
        val errors = AttributeCodec.validate(type.attributeSchema, edit.values)
        if (errors.isNotEmpty()) {
            editor.value = edit.copy(errors = errors)
            return
        }
        editor.value = null
        viewModelScope.launch {
            writeAttributes(equipment, type, edit.values)
            message.value = SheetMessage.ATTRIBUTES_SAVED
        }
    }

    /**
     * Tick or clear one monthly-checklist box — §9.3.
     *
     * Checklist boxes are a quick action, so they persist on the tap rather than waiting for a save.
     * While the full attribute editor is open the tick goes into the draft instead, so the officer
     * never has two competing copies of the same value.
     */
    fun toggleChecklistItem(key: String, checked: Boolean) {
        val open = editor.value
        if (open != null) {
            updateAttribute(key, checked.toString())
            return
        }
        val state = uiState.value
        val equipment = state.equipment ?: return
        val type = state.type ?: return
        viewModelScope.launch {
            writeAttributes(equipment, type, state.attributeValues + (key to checked.toString()))
        }
    }

    private suspend fun writeAttributes(equipment: Equipment, type: EquipmentType, values: AttributeDraft) {
        val json = AttributeCodec.encodeToString(
            schema = type.attributeSchema,
            values = values,
            carryOver = AttributeCodec.unknownValues(type.attributeSchema, equipment.attributesJson),
        )
        equipmentRepository.upsertEquipment(
            equipment.copy(attributesJson = json, updatedAt = Dates.nowMillis()),
        )
        maintenanceRepository.recomputeDue(equipment.id)
    }

    /**
     * Close the monthly task with a completed checklist — §9.3.
     *
     * The task key comes from [MonthlyChecklist.taskKeyFor]; the occurrence closed is the earliest
     * open instance of that key. When the item has no instance yet (a record created before its
     * schedule was computed) the schedule is recomputed once and the lookup retried, so the officer
     * is not blocked by a bookkeeping gap.
     */
    fun logMonthlyInspection() {
        val state = uiState.value
        val equipment = state.equipment ?: return
        val taskKey = state.monthlyTaskKey
        if (taskKey == null) {
            message.value = SheetMessage.MONTHLY_NO_TASK
            return
        }
        viewModelScope.launch {
            var instance = MonthlyChecklist.openInstanceFor(
                maintenanceRepository.observeTaskInstances(equipment.id).first(),
                taskKey,
            )
            if (instance == null) {
                maintenanceRepository.recomputeDue(equipment.id)
                instance = MonthlyChecklist.openInstanceFor(
                    maintenanceRepository.observeTaskInstances(equipment.id).first(),
                    taskKey,
                )
            }
            val target = instance
            if (target == null) {
                message.value = SheetMessage.MONTHLY_NO_TASK
                return@launch
            }
            maintenanceRepository.completeTask(
                instanceId = target.id,
                completedDate = Dates.todayEpochDay(),
                completedBy = null,
                serviceProvider = null,
                certificateNumber = null,
                findings = null,
                conditionAfter = equipment.condition,
            )
            message.value = SheetMessage.MONTHLY_LOGGED
        }
    }

    // ------------------------------------------------------------------ §7.4 destructive actions

    /** Duplicate ×N with auto-incremented tags, then schedule the copies — §7.5. */
    fun duplicate(count: Int) {
        val id = uiState.value.equipment?.id ?: return
        if (count <= 0) return
        viewModelScope.launch {
            equipmentRepository.duplicate(id, count).forEach { maintenanceRepository.recomputeDue(it) }
            message.value = SheetMessage.DUPLICATED
        }
    }

    /** Move the item to another deck; position is reset to the centre for the host to drag out. */
    fun moveToDeck(deckId: String?, zoneId: String? = null) {
        val current = uiState.value.equipment ?: return
        viewModelScope.launch {
            equipmentRepository.move(current.id, deckId, zoneId, DEFAULT_POSITION, DEFAULT_POSITION)
        }
    }

    /**
     * Arm a local reminder for this item — §11.3.
     *
     * Deliberately not a task and not a deficiency: it is a private nudge, so nothing about the
     * record or the schedule changes. If notifications are off or blocked, nothing arrives and
     * nothing breaks.
     */
    fun remindIn(days: Int) {
        val current = uiState.value.equipment ?: return
        itemReminders.scheduleIn(current.id, current.tag, days)
    }

    /** Drop a pending reminder for this item. */
    fun cancelReminder() {
        val current = uiState.value.equipment ?: return
        itemReminders.cancel(current.id)
    }

    /**
     * Record a photo the camera has just written — §7.6.
     *
     * The file already exists on disk by the time this runs (the capture wrote into it), so the
     * only thing left is to append its URI. Re-adding a URI already on the record is a no-op, which
     * makes a duplicated result callback harmless.
     */
    fun addPhoto(uri: String) {
        val current = uiState.value.equipment ?: return
        if (uri in current.photoUris) return
        viewModelScope.launch {
            val stored = equipmentRepository.getEquipment(current.id) ?: return@launch
            if (uri in stored.photoUris) return@launch
            equipmentRepository.upsertEquipment(
                stored.copy(photoUris = stored.photoUris + uri, updatedAt = Dates.nowMillis()),
            )
        }
    }

    /**
     * Drop a photo from the record. The caller deletes the file itself — this owns the record, not
     * the filesystem, and a failed delete must not leave a URI pointing at nothing.
     */
    fun removePhoto(uri: String) {
        val current = uiState.value.equipment ?: return
        viewModelScope.launch {
            val stored = equipmentRepository.getEquipment(current.id) ?: return@launch
            if (uri !in stored.photoUris) return@launch
            equipmentRepository.upsertEquipment(
                stored.copy(photoUris = stored.photoUris - uri, updatedAt = Dates.nowMillis()),
            )
        }
    }

    /**
     * Soft-delete, and hand the caller the undo — C10.
     *
     * Nothing is destroyed: `deletedAt` is stamped, the record leaves every observation, and
     * [onDeleted]'s second argument puts it back. The host owns the ten-second snackbar.
     */
    fun delete(onDeleted: (String, suspend () -> Unit) -> Unit) {
        val id = uiState.value.equipment?.id ?: return
        viewModelScope.launch {
            equipmentRepository.softDelete(id, Dates.nowMillis())
            onDeleted(id) {
                equipmentRepository.undelete(id)
                maintenanceRepository.recomputeDue(id)
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    // ------------------------------------------------------------------ content assembly

    private fun observeContent(id: String): Flow<LoadedContent> = flow {
        val initial = equipmentRepository.getEquipment(id)
        if (initial == null || initial.deletedAt != null) {
            emit(LoadedContent.missing())
            return@flow
        }
        val type = referenceRepository.getEquipmentType(initial.typeKey)
        val live = combine(
            equipmentRepository.observeEquipment(initial.vesselId).map { list -> list.firstOrNull { it.id == id } },
            maintenanceRepository.observeTaskInstances(id),
            maintenanceRepository.observeTaskDefinitions(),
            inspectionRepository.observeOpenDeficiencies(initial.vesselId)
                .map { list -> list.filter { it.equipmentId == id } },
            referenceRepository.observeRegulationCards(),
        ) { equipment, instances, definitions, deficiencies, cards ->
            if (equipment == null) {
                LoadedContent.missing()
            } else {
                LoadedContent(build(equipment, type, instances, definitions, deficiencies, cards))
            }
        }
        emitAll(live)
    }

    @Suppress("LongParameterList") // One assembly point for the whole sheet.
    private fun build(
        equipment: Equipment,
        type: EquipmentType?,
        instances: List<TaskInstance>,
        definitions: List<TaskDefinition>,
        deficiencies: List<Deficiency>,
        cards: List<RegulationCard>,
    ): EquipmentSheetUiState {
        val schema = type?.attributeSchema.orEmpty()
        val values = AttributeCodec.decode(schema, equipment.attributesJson)
        val definitionsByKey = definitions.associateBy { it.key }
        val checklistItems = type?.let(MonthlyChecklist::items).orEmpty()

        val tasks = instances
            .map { instance ->
                val definition = definitionsByKey[instance.taskKey]
                TaskRowUi(
                    instanceId = instance.id,
                    taskKey = instance.taskKey,
                    titleEn = definition?.titleEn ?: instance.taskKey,
                    titleTr = definition?.titleTr.orEmpty(),
                    dueDate = instance.dueDate,
                    status = instance.status,
                    performedBy = definition?.performedBy ?: DEFAULT_PERFORMED_BY,
                    completedDate = instance.completedDate,
                )
            }
            .sortedWith(compareBy({ it.status == TaskStatus.DONE }, { it.dueDate }, { it.taskKey }))

        // §8.4: the type's own references plus every reference of the tasks that apply to it.
        val taskKeys = (type?.taskKeys.orEmpty() + instances.map { it.taskKey }).distinct()
        val refKeys = (
            type?.regulationRefs.orEmpty() +
                taskKeys.flatMap { key -> definitionsByKey[key]?.regulationRefs.orEmpty() }
            ).distinct()
        val cardsByKey = cards.associateBy { it.refKey }

        return EquipmentSheetUiState(
            loading = false,
            equipment = equipment,
            type = type,
            attributeValues = values,
            checklist = checklistItems.map { item ->
                ChecklistItemUi(
                    key = item.key,
                    labelEn = item.labelEn,
                    labelTr = item.labelTr,
                    checked = AttributeCodec.isTicked(values[item.key]),
                )
            },
            checklistComplete = MonthlyChecklist.allTicked(checklistItems, values),
            monthlyTaskKey = type?.let(MonthlyChecklist::taskKeyFor),
            tasks = tasks,
            openDeficiencies = deficiencies,
            requirements = refKeys.mapNotNull { cardsByKey[it] },
            lastInspection = instances
                .filter { it.status == TaskStatus.DONE }
                .mapNotNull { it.completedDate }
                .maxOrNull(),
            todayEpochDay = Dates.todayEpochDay(),
        )
    }

    /** Wrapper so the content flow can carry "idle" and "missing" without a sealed hierarchy. */
    private data class LoadedContent(val state: EquipmentSheetUiState) {
        companion object {
            fun idle() = LoadedContent(EquipmentSheetUiState())
            fun missing() = LoadedContent(EquipmentSheetUiState(loading = false, missing = true))
        }
    }

    companion object {
        /** Every destructive action is undoable for ten seconds — C10, §7.3. */
        const val UNDO_WINDOW_MILLIS: Long = 10_000L

        private const val DEFAULT_POSITION = 0.5f
        private const val DECKS_STOP_TIMEOUT_MILLIS = 5_000L
        private val DEFAULT_PERFORMED_BY = PerformedBy.SHIP_STAFF
    }
}
