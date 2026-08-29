package com.deckwatch.core.testing

import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.due.VesselDueContext
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [MaintenanceRepository] that drives a real [DueEngine], so a test that completes a
 * task sees the next occurrence appear exactly as the app would — §11.2 ("on any write to
 * equipment, attributes, or task completion").
 *
 * @param equipment the equipment store the due engine reads from and denormalises onto (§11.1 (5)).
 * @param reference supplies the [com.deckwatch.core.model.EquipmentType] catalogue (§9.1).
 * @param vessels supplies the flag and Safety Equipment Certificate expiry (§11.1 (1), (3)).
 * @param today the epoch-day the engine treats as today; override for deterministic tests.
 */
class FakeMaintenanceRepository(
    private val equipment: FakeEquipmentRepository = FakeEquipmentRepository(),
    private val reference: FakeReferenceRepository = FakeReferenceRepository(),
    private val vessels: FakeVesselRepository = FakeVesselRepository(),
    private val engine: DueEngine = DueEngine(),
    private val leadTimeDays: Int = DueEngine.DEFAULT_LEAD_TIME_DAYS,
    private val today: () -> Long = Dates::todayEpochDay,
    private val clock: () -> Long = Dates::nowMillis,
) : MaintenanceRepository {

    val definitions = MutableStateFlow<Map<String, TaskDefinition>>(emptyMap())
    val instances = MutableStateFlow<Map<String, TaskInstance>>(emptyMap())

    override fun observeTaskDefinitions(): Flow<List<TaskDefinition>> =
        definitions.map { current -> current.values.sortedBy { it.key } }

    override suspend fun getTaskDefinition(key: String): TaskDefinition? = definitions.value[key]

    override suspend fun upsertTaskDefinition(definition: TaskDefinition) {
        definitions.update { it + (definition.key to definition) }
    }

    override fun observeTaskInstances(equipmentId: String): Flow<List<TaskInstance>> =
        instances.map { current ->
            current.values.filter { it.equipmentId == equipmentId }.sortedBy { it.dueDate }
        }

    /** Everything still owed on a vessel: `DONE` and `NOT_APPLICABLE` are filtered out — §11.1 (5). */
    override fun observeOpenInstancesForVessel(vesselId: String): Flow<List<TaskInstance>> =
        instances.map { current ->
            val onVessel = equipment.equipment.value.values
                .filter { it.vesselId == vesselId && it.deletedAt == null }
                .map { it.id }
                .toSet()
            current.values
                .filter { it.equipmentId in onVessel && it.status !in CLOSED_STATUSES }
                .sortedBy { it.dueDate }
        }

    override suspend fun upsertInstances(instances: List<TaskInstance>) {
        this.instances.update { current -> current + instances.associateBy { it.id } }
    }

    override suspend fun completeTask(
        instanceId: String,
        completedDate: Long,
        completedBy: String?,
        serviceProvider: String?,
        certificateNumber: String?,
        findings: String?,
        conditionAfter: ConditionGrade?,
    ) {
        val existing = instances.value[instanceId] ?: return
        val done = existing.copy(
            status = TaskStatus.DONE,
            completedDate = completedDate,
            completedBy = completedBy,
            serviceProvider = serviceProvider,
            certificateNumber = certificateNumber,
            findings = findings,
            conditionAfter = conditionAfter,
            updatedAt = clock(),
        )
        instances.update { it + (done.id to done) }
        recomputeDue(existing.equipmentId)
    }

    /** Re-derive and persist due state for one equipment item — §11.1, §11.2. */
    override suspend fun recomputeDue(equipmentId: String) {
        val item = equipment.getEquipment(equipmentId) ?: return
        if (item.deletedAt != null) return
        val type = reference.getEquipmentType(item.typeKey) ?: return
        val context = vessels.getVessel(item.vesselId)
            ?.let(VesselDueContext::from)
            ?: VesselDueContext()
        val result = engine.computeForEquipment(
            equipment = item,
            type = type,
            definitions = definitions.value,
            existingInstances = instances.value.values.toList(),
            vessel = context,
            todayEpochDay = today(),
            leadTimeDays = leadTimeDays,
            nowMillis = clock(),
        )
        upsertInstances(result.instancesToUpsert)
        equipment.upsertEquipment(result.applyTo(item, clock()))
    }

    override suspend fun recomputeDueForVessel(vesselId: String) {
        equipment.equipment.value.values
            .filter { it.vesselId == vesselId && it.deletedAt == null }
            .map { it.id }
            .forEach { recomputeDue(it) }
    }

    private companion object {
        val CLOSED_STATUSES = setOf(TaskStatus.DONE, TaskStatus.NOT_APPLICABLE)
    }
}
