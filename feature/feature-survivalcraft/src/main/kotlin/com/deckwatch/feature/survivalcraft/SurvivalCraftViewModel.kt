package com.deckwatch.feature.survivalcraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.ReferenceRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.feature.survivalcraft.drill.DrillLog
import com.deckwatch.feature.survivalcraft.drill.DrillNotes
import com.deckwatch.feature.survivalcraft.inventory.InventoryCodec
import com.deckwatch.feature.survivalcraft.inventory.InventoryItem
import com.deckwatch.feature.survivalcraft.inventory.InventoryTemplates
import com.deckwatch.feature.survivalcraft.inventory.expirySummary
import com.deckwatch.feature.survivalcraft.schematic.SchematicCatalogue
import com.deckwatch.feature.survivalcraft.schematic.SchematicDef
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State and writes for the survival-craft / fixed-system schematic screen — §7.6.
 *
 * Repository interfaces only; this module never sees Room. Sub-components are ordinary equipment
 * rows found through [EquipmentRepository.observeChildren] (§6.5), the inventory is an attribute
 * of the parent, and a drill is a [Round] — no table is added anywhere.
 *
 * @param today epoch-day treated as today, injected as a lambda so every boundary (expiry counts,
 *   days-since-last-launch) is testable against a fixed day rather than the wall clock.
 * @param clock epoch-millis for `updatedAt` stamps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SurvivalCraftViewModel(
    private val equipmentRepository: EquipmentRepository,
    private val referenceRepository: ReferenceRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val inspectionRepository: InspectionRepository,
    private val catalogue: SchematicCatalogue,
    private val today: () -> Long = Dates::todayEpochDay,
    private val clock: () -> Long = Dates::nowMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    /** The graph's entry point; the clocks are ambient state, not dependencies to be bound. */
    @Inject
    constructor(
        equipmentRepository: EquipmentRepository,
        referenceRepository: ReferenceRepository,
        maintenanceRepository: MaintenanceRepository,
        inspectionRepository: InspectionRepository,
        catalogue: SchematicCatalogue,
    ) : this(
        equipmentRepository = equipmentRepository,
        referenceRepository = referenceRepository,
        maintenanceRepository = maintenanceRepository,
        inspectionRepository = inspectionRepository,
        catalogue = catalogue,
        today = Dates::todayEpochDay,
        clock = Dates::nowMillis,
        newId = { UUID.randomUUID().toString() },
    )

    private val boundId = MutableStateFlow<String?>(null)
    private val completionDraft = MutableStateFlow<TaskCompletionDraft?>(null)
    private val drillDraft = MutableStateFlow<DrillDraft?>(null)
    private val message = MutableStateFlow<CraftMessage?>(null)

    private val content: Flow<SurvivalCraftUiState> = boundId.flatMapLatest { id ->
        if (id == null) flowOf(SurvivalCraftUiState()) else observeContent(id)
    }

    /**
     * Started eagerly and kept hot: the write actions read `uiState.value` to find the record
     * they act on, so the state must be current the instant the screen opens.
     */
    val uiState: StateFlow<SurvivalCraftUiState> =
        combine(content, completionDraft, drillDraft, message) { loaded, completion, drill, note ->
            loaded.copy(completionDraft = completion, drillDraft = drill, message = note)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SurvivalCraftUiState())

    /** Point the screen at a parent craft; re-binding to the same id is a no-op. */
    fun bind(equipmentId: String) {
        if (boundId.value == equipmentId) return
        boundId.value = equipmentId
        completionDraft.value = null
        drillDraft.value = null
        message.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    // ------------------------------------------------------------------ hotspots and children

    /**
     * Adopt rows just created from a hotspot's "add" state.
     *
     * [com.deckwatch.feature.equipment.AddEquipmentSheet] has no `parentId` parameter, so the
     * link is made here, right after `onCreated`: each new row is re-read, given the parent's id
     * and the hotspot binding of [HOTSPOT_ATTRIBUTE_KEY], and written back through
     * [EquipmentRepository.upsertEquipment]. The due schedule is then recomputed for the new row,
     * because §11.2 requires it on any write to equipment.
     */
    fun adoptChildren(ids: List<String>, hotspotKey: String?) {
        val parent = uiState.value.equipment ?: return
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEachIndexed { index, id ->
                val created = equipmentRepository.getEquipment(id) ?: return@forEachIndexed
                // Only the first of a duplicated batch takes the hotspot binding; the rest are
                // plain sub-components of the same parent.
                val linked = created.copy(parentId = parent.id, updatedAt = clock())
                val bound = if (index == 0 && hotspotKey != null) {
                    linked.withHotspotBinding(hotspotKey)
                } else {
                    linked
                }
                equipmentRepository.upsertEquipment(bound)
                maintenanceRepository.recomputeDue(bound.id)
            }
            message.value = CraftMessage.CHILD_LINKED
        }
    }

    // ------------------------------------------------------------------ inventory

    fun updateInventoryItem(key: String, transform: (InventoryItem) -> InventoryItem) {
        val current = uiState.value
        val parent = current.equipment ?: return
        val items = current.inventory.map { it.item }
        val updated = items.map { if (it.key == key) transform(it) else it }
        persistInventory(parent, updated)
    }

    fun addInventoryRow(label: String) {
        val current = uiState.value
        val parent = current.equipment ?: return
        val template = current.inventoryTemplate ?: return
        if (!template.addable) return
        val items = current.inventory.map { it.item }
        val item = InventoryItem(
            key = InventoryCodec.nextAddedKey(template, items),
            quantity = 1,
            label = label,
        )
        persistInventory(parent, items + item)
    }

    fun removeInventoryRow(key: String) {
        val current = uiState.value
        val parent = current.equipment ?: return
        val items = current.inventory.map { it.item }.filterNot { it.key == key }
        persistInventory(parent, items)
    }

    private fun persistInventory(parent: Equipment, items: List<InventoryItem>) {
        viewModelScope.launch {
            equipmentRepository.upsertEquipment(InventoryCodec.apply(parent, items, clock()))
            message.value = CraftMessage.INVENTORY_SAVED
        }
    }

    // ------------------------------------------------------------------ tasks

    fun openCompletion(instanceId: String) {
        val row = uiState.value.taskGroups
            .flatMap { it.rows }
            .firstOrNull { it.instanceId == instanceId } ?: return
        completionDraft.value = TaskCompletionDraft(
            instanceId = row.instanceId,
            titleEn = row.titleEn,
            titleTr = row.titleTr,
            completedDate = today(),
            needsProvider = row.needsProvider,
        )
    }

    fun updateCompletion(transform: (TaskCompletionDraft) -> TaskCompletionDraft) {
        completionDraft.value = completionDraft.value?.let(transform)
    }

    fun dismissCompletion() {
        completionDraft.value = null
    }

    fun saveCompletion() {
        val draft = completionDraft.value ?: return
        val completedDate = draft.completedDate ?: return
        if (!draft.isValid) return
        completionDraft.value = null
        viewModelScope.launch {
            maintenanceRepository.completeTask(
                instanceId = draft.instanceId,
                completedDate = completedDate,
                completedBy = draft.completedBy.ifBlank { null },
                serviceProvider = draft.serviceProvider.ifBlank { null },
                certificateNumber = draft.certificateNumber.ifBlank { null },
                findings = draft.findings.ifBlank { null },
                conditionAfter = null,
            )
            message.value = CraftMessage.TASK_LOGGED
        }
    }

    // ------------------------------------------------------------------ drills

    fun openDrill() {
        drillDraft.value = DrillDraft(dateEpochDay = today())
    }

    fun updateDrill(transform: (DrillDraft) -> DrillDraft) {
        drillDraft.value = drillDraft.value?.let(transform)
    }

    fun dismissDrill() {
        drillDraft.value = null
    }

    /**
     * Write a SOLAS III/19 drill record as a [Round] — see [DrillLog] for the encoding.
     *
     * @param title the localised default title supplied by the screen, so the record reads in the
     *   officer's own language.
     */
    fun saveDrill(title: String) {
        val draft = drillDraft.value ?: return
        val date = draft.dateEpochDay ?: return
        val parent = uiState.value.equipment ?: return
        if (!draft.isValid) return
        drillDraft.value = null
        val startedAt = DrillLog.toMillis(date)
        viewModelScope.launch {
            inspectionRepository.upsertRound(
                Round(
                    id = newId(),
                    vesselId = parent.vesselId,
                    templateKey = DrillLog.templateKey(parent.typeKey),
                    title = title,
                    startedAt = startedAt,
                    completedAt = startedAt,
                    performedBy = draft.performedBy,
                    notes = DrillLog.encodeNotes(
                        DrillNotes(
                            equipmentId = parent.id,
                            launched = draft.launched,
                            remarks = draft.remarks,
                        ),
                    ),
                ),
            )
            message.value = CraftMessage.DRILL_LOGGED
        }
    }

    // ------------------------------------------------------------------ assembly

    /** The five live streams the screen is assembled from, gathered so they combine as one. */
    private data class Sources(
        val parent: Equipment?,
        val children: List<Equipment>,
        val instances: List<TaskInstance>,
        val definitions: List<TaskDefinition>,
        val rounds: List<Round>,
    )

    private fun observeContent(id: String): Flow<SurvivalCraftUiState> = flow {
        val initial = equipmentRepository.getEquipment(id)
        if (initial == null || initial.deletedAt != null) {
            emit(SurvivalCraftUiState(loading = false, missing = true))
            return@flow
        }
        val type = referenceRepository.getEquipmentType(initial.typeKey)
        val schematic = catalogue.forTypeKey(initial.typeKey)
        val sources = combine(
            equipmentRepository.observeEquipment(initial.vesselId)
                .map { list -> list.firstOrNull { it.id == id } },
            equipmentRepository.observeChildren(id),
            maintenanceRepository.observeTaskInstances(id),
            maintenanceRepository.observeTaskDefinitions(),
            inspectionRepository.observeRounds(initial.vesselId),
            ::Sources,
        )
        val live = combine(sources, referenceRepository.observeEquipmentTypes()) { current, types ->
            val parent = current.parent
            if (parent == null) {
                SurvivalCraftUiState(loading = false, missing = true)
            } else {
                build(parent, type, schematic, current, types)
            }
        }
        emitAll(live)
    }

    private fun build(
        parent: Equipment,
        type: EquipmentType?,
        schematic: SchematicDef,
        sources: Sources,
        types: List<EquipmentType>,
    ): SurvivalCraftUiState {
        val children = sources.children
        val instances = sources.instances
        val definitions = sources.definitions
        val rounds = sources.rounds
        val typesByKey = types.associateBy { it.typeKey }
        val todayDay = today()
        val matches = HotspotMatching.match(schematic.hotspots, children)
        val hotspotLabels = schematic.hotspots.associateBy { it.key }

        val hotspots = matches.map { match ->
            HotspotUi(
                hotspot = match.hotspot,
                childId = match.child?.id,
                childTag = match.child?.tag,
                condition = match.child?.condition ?: ConditionGrade.NOT_CHECKED,
                nextDueDate = match.child?.nextDueDate,
            )
        }
        val hotspotByChildId = matches
            .mapNotNull { m -> m.child?.let { it.id to m.hotspot } }
            .toMap()

        val components = children.sortedBy { it.tag }.map { child ->
            val hotspot = hotspotByChildId[child.id] ?: child.boundHotspotKey()?.let { hotspotLabels[it] }
            val childType = typesByKey[child.typeKey]
            ComponentRowUi(
                id = child.id,
                tag = child.tag,
                typeNameEn = child.name ?: childType?.nameEn ?: child.typeKey,
                typeNameTr = child.name ?: childType?.nameTr.orEmpty(),
                condition = child.condition,
                nextDueDate = child.nextDueDate,
                hotspotLabelEn = hotspot?.labelEn,
                hotspotLabelTr = hotspot?.labelTr,
            )
        }

        val template = InventoryTemplates.forKey(schematic.inventoryTemplateKey)
        val storedInventory = InventoryCodec.decode(parent.attributesJson)
        val inventoryItems = InventoryCodec.merge(template, storedInventory)
        val inventory = inventoryItems.map { item ->
            InventoryRowUi(
                item = item,
                expires = template?.expiringKeys?.contains(item.key) == true || item.expiryEpochDay != null,
                userAdded = template == null || item.key !in template.itemKeys,
            )
        }

        val definitionsByKey = definitions.associateBy { it.key }
        val taskRows = instances.map { instance ->
            val definition = definitionsByKey[instance.taskKey]
            TaskRowUi(
                instanceId = instance.id,
                taskKey = instance.taskKey,
                titleEn = definition?.titleEn ?: instance.taskKey,
                titleTr = definition?.titleTr.orEmpty(),
                dueDate = instance.dueDate,
                status = instance.status,
                performedBy = definition?.performedBy ?: PerformedBy.SHIP_STAFF,
                completedDate = instance.completedDate,
                needsProvider = definition?.performedBy?.let { it !in CREW_PERFORMED } ?: false,
            )
        }
        val taskGroups = taskRows
            .groupBy { TaskGroup.of(definitionsByKey[it.taskKey]?.intervalKind) }
            .toSortedMap(compareBy { it.ordinal })
            .map { (group, rows) ->
                TaskGroupUi(
                    group = group,
                    rows = rows.sortedWith(
                        compareBy({ it.status == TaskStatus.DONE }, { it.dueDate }, { it.taskKey }),
                    ),
                )
            }

        val drills = DrillLog.recordsFor(parent.id, parent.typeKey, rounds)

        return SurvivalCraftUiState(
            loading = false,
            missing = false,
            equipment = parent,
            type = type,
            schematic = schematic,
            hotspots = hotspots,
            components = components,
            inventoryTemplate = template,
            inventory = inventory,
            inventorySummary = inventoryItems.expirySummary(todayDay),
            taskGroups = taskGroups,
            drills = drills,
            daysSinceLastLaunch = DrillLog.daysSinceLastLaunch(drills, todayDay),
            lastDrillDay = DrillLog.lastDrillDay(drills),
            todayEpochDay = todayDay,
        )
    }

    private companion object {
        /** Who counts as "the ship can do this itself" for the provider fields — §11.4. */
        val CREW_PERFORMED = setOf(PerformedBy.SHIP_STAFF, PerformedBy.SHIP_STAFF_TRAINED)
    }
}
