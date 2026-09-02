package com.deckwatch.data.repository

import androidx.room.withTransaction
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.due.EngineResult
import com.deckwatch.core.common.due.VesselDueContext
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.dao.EquipmentTypeDao
import com.deckwatch.core.database.dao.TaskDefinitionDao
import com.deckwatch.core.database.dao.TaskInstanceDao
import com.deckwatch.core.database.dao.VesselDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Task definitions, task instances, and the persistence half of the due engine — MASTER_PROMPT §11.
 *
 * This is the orchestrator: [DueEngine] is a pure function of its inputs and deliberately knows
 * nothing about storage, so everything that has to be *consistent* lives here.
 *
 * ### The recomputation transaction (§11.2 "immediate, in-transaction")
 *
 * [recomputeDue] loads, in one Room transaction:
 * equipment · its catalogue type · every task definition · every instance the item already has ·
 * its vessel's flag and Safety Equipment Certificate expiry. The user's due lead time (§18) is read
 * from the settings store **before** the transaction opens — a DataStore read must never be waited
 * on while a write transaction is held. It then writes, still inside the transaction:
 *
 * 1. `EngineResult.instancesToUpsert` — updates in place, because the engine reuses the id of the
 *    open occurrence for a key (or derives a deterministic one for a new occurrence);
 * 2. **the prune** — see below;
 * 3. `equipment.nextDueDate` / `nextDueTaskKey`, the denormalisation of §11.1 (5) that lets the
 *    plan view colour 600 markers without a join.
 *
 * Either all three land or none does, so the Due tab and the marker colours can never disagree
 * about the same item (C10).
 *
 * ### Pruning — this layer's job, not the engine's
 *
 * The engine returns nothing for a task key it no longer derives, and explicitly leaves the
 * existing rows alone: pruning needs the transaction, so it belongs here. The rule is deliberately
 * narrow. An instance is deleted only when **all** of:
 *
 * * its `taskKey` is no longer in the derived set — the officer changed an attribute that drives
 *   task selection (§9.3), e.g. an extinguisher's medium from `CO2` to `DRY_POWDER_ABC`, so the
 *   cylinder-weight check no longer applies; **and**
 * * it is still open — `DONE` and `NOT_APPLICABLE` rows are never touched; **and**
 * * it carries no completion of its own (`completedDate == null`).
 *
 * Everything the ship actually did stays: a `DONE` cylinder-weight check from last year survives
 * the medium change and remains in the item's history and in every export. What disappears is only
 * work that was scheduled and never performed, for a task that no longer applies — which is exactly
 * the row that would otherwise sit in the Due tab for ever with nobody able to close it.
 *
 * The same rule cleans up after the officer marks a task `NOT_APPLICABLE`: the engine then emits
 * nothing for that key, so any *other* still-open occurrence of it — which would contradict the
 * officer's statement — is removed, while the `NOT_APPLICABLE` row itself is kept as the record of
 * that decision.
 */
@Singleton
class MaintenanceRepositoryImpl @Inject constructor(
    private val database: DeckWatchDatabase,
    private val equipmentDao: EquipmentDao,
    private val equipmentTypeDao: EquipmentTypeDao,
    private val taskDefinitionDao: TaskDefinitionDao,
    private val taskInstanceDao: TaskInstanceDao,
    private val vesselDao: VesselDao,
    private val preferences: UserPreferencesRepository,
    private val engine: DueEngine,
    private val dispatchers: DispatcherProvider,
    private val time: TimeSource,
) : MaintenanceRepository {

    override fun observeTaskDefinitions(): Flow<List<TaskDefinition>> =
        taskDefinitionDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getTaskDefinition(key: String): TaskDefinition? =
        withContext(dispatchers.io) { taskDefinitionDao.getByKey(key)?.toModel() }

    override suspend fun upsertTaskDefinition(definition: TaskDefinition) =
        withContext(dispatchers.io) { taskDefinitionDao.upsert(definition.toEntity()) }

    override fun observeTaskInstances(equipmentId: String): Flow<List<TaskInstance>> =
        taskInstanceDao.observeByEquipment(equipmentId).map { rows -> rows.map { it.toModel() } }

    override fun observeOpenInstancesForVessel(vesselId: String): Flow<List<TaskInstance>> =
        taskInstanceDao.observeOpenForVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertInstances(instances: List<TaskInstance>) =
        withContext(dispatchers.io) {
            taskInstanceDao.upsertAll(instances.map { it.toEntity() })
        }

    /**
     * Record a completion and immediately re-derive the item — §11.2.
     *
     * Both writes share one transaction: the moment the officer's signed monthly check lands, the
     * next occurrence is already scheduled from it. Nothing can observe a completed task with no
     * successor.
     */
    override suspend fun completeTask(
        instanceId: String,
        completedDate: Long,
        completedBy: String?,
        serviceProvider: String?,
        certificateNumber: String?,
        findings: String?,
        conditionAfter: ConditionGrade?,
    ) = withContext(dispatchers.io) {
        val leadTimeDays = preferences.get().dueLeadTimeDays
        database.withTransaction {
            val instance = taskInstanceDao.getById(instanceId) ?: return@withTransaction
            taskInstanceDao.complete(
                id = instanceId,
                completedDate = completedDate,
                completedBy = completedBy,
                serviceProvider = serviceProvider,
                certificateNumber = certificateNumber,
                findings = findings,
                conditionAfter = conditionAfter,
                atMillis = time.nowMillis(),
            )
            recomputeInTransaction(
                equipmentId = instance.equipmentId,
                definitions = allDefinitions(),
                leadTimeDays = leadTimeDays,
            )
        }
    }

    override suspend fun recomputeDue(equipmentId: String) = withContext(dispatchers.io) {
        val leadTimeDays = preferences.get().dueLeadTimeDays
        database.withTransaction {
            recomputeInTransaction(
                equipmentId = equipmentId,
                definitions = allDefinitions(),
                leadTimeDays = leadTimeDays,
            )
        }
    }

    /**
     * Re-derive a whole vessel — the cold-start, vessel-switch and 03:00 worker path of §11.2.
     *
     * The definition table and the catalogue types are read **once** for the whole vessel rather
     * than once per item: a 300-item register would otherwise re-read the same few dozen rows
     * three hundred times. Everything is one transaction, so a vessel is never half-recomputed.
     */
    override suspend fun recomputeDueForVessel(vesselId: String) = withContext(dispatchers.io) {
        val leadTimeDays = preferences.get().dueLeadTimeDays
        database.withTransaction {
            val definitions = allDefinitions()
            val types = equipmentTypeDao.getAll().associate { it.typeKey to it.toModel() }
            val vesselContext = vesselDao.getById(vesselId)
                ?.let { VesselDueContext.from(it.toModel()) }
                ?: VesselDueContext()
            for (row in equipmentDao.getByVessel(vesselId)) {
                val equipment = row.toModel()
                val type = types[equipment.typeKey] ?: continue
                applyRecomputation(equipment, type, definitions, vesselContext, leadTimeDays)
            }
        }
    }

    private suspend fun allDefinitions(): Map<String, TaskDefinition> =
        taskDefinitionDao.getAll().associate { it.key to it.toModel() }

    /**
     * One item's recomputation, assuming the caller already holds the transaction. An item whose
     * catalogue type is missing from the database (a register imported before its type was seeded)
     * is skipped rather than throwing — §11.2 runs this on every write and must not be able to
     * fail a save.
     */
    private suspend fun recomputeInTransaction(
        equipmentId: String,
        definitions: Map<String, TaskDefinition>,
        leadTimeDays: Int,
    ) {
        val equipment = equipmentDao.getById(equipmentId)?.toModel() ?: return
        val type = equipmentTypeDao.getByKey(equipment.typeKey)?.toModel() ?: return
        val vesselContext = vesselDao.getById(equipment.vesselId)
            ?.let { VesselDueContext.from(it.toModel()) }
            ?: VesselDueContext()
        applyRecomputation(equipment, type, definitions, vesselContext, leadTimeDays)
    }

    private suspend fun applyRecomputation(
        equipment: Equipment,
        type: EquipmentType,
        definitions: Map<String, TaskDefinition>,
        vesselContext: VesselDueContext,
        leadTimeDays: Int,
    ) {
        val now = time.nowMillis()
        val existing = taskInstanceDao.getByEquipment(equipment.id).map { it.toModel() }
        val result = engine.computeForEquipment(
            equipment = equipment,
            type = type,
            definitions = definitions,
            existingInstances = existing,
            vessel = vesselContext,
            todayEpochDay = time.todayEpochDay(),
            leadTimeDays = leadTimeDays,
            nowMillis = now,
        )
        taskInstanceDao.upsertAll(result.instancesToUpsert.map { it.toEntity() })
        prune(existing, result)
        equipmentDao.setNextDue(
            id = equipment.id,
            nextDueDate = result.nextDueDate,
            nextDueTaskKey = result.nextDueTaskKey,
            atMillis = now,
        )
    }

    /** See the class KDoc: open, un-performed occurrences of keys the engine no longer derives. */
    private suspend fun prune(existing: List<TaskInstance>, result: EngineResult) {
        val derivedKeys = result.instancesToUpsert.mapTo(HashSet()) { it.taskKey }
        val writtenIds = result.instancesToUpsert.mapTo(HashSet()) { it.id }
        for (instance in existing) {
            val stale = instance.taskKey !in derivedKeys &&
                instance.id !in writtenIds &&
                instance.status !in CLOSED_STATUSES &&
                instance.completedDate == null
            if (stale) taskInstanceDao.deleteById(instance.id)
        }
    }

    private companion object {
        /**
         * History (`DONE`) and the officer's explicit "this does not apply here"
         * (`NOT_APPLICABLE`) are never pruned — they are records, not schedule.
         */
        val CLOSED_STATUSES = setOf(TaskStatus.DONE, TaskStatus.NOT_APPLICABLE)
    }
}
