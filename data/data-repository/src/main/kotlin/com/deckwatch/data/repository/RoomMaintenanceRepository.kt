package com.deckwatch.data.repository

import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.due.VesselDueContext
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.database.TransactionRunner
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.dao.EquipmentTypeDao
import com.deckwatch.core.database.dao.TaskDefinitionDao
import com.deckwatch.core.database.dao.TaskInstanceDao
import com.deckwatch.core.database.dao.VesselDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [MaintenanceRepository] — MASTER_PROMPT §6.6 and §11.
 *
 * This is where the pure [DueEngine] meets the database: the engine decides *what* is due, this
 * class loads its inputs, writes the instances it returns and denormalises the soonest one onto
 * the equipment row (§11.1 step 5). Both writes happen inside one [TransactionRunner] call, so a
 * reader never sees new task instances without the matching `nextDueDate`, or the reverse.
 *
 * The lead time comes from the user's setting on every recomputation rather than being cached:
 * changing it in Settings (§18) must be visible the next time anything is recomputed.
 */
@Singleton
class RoomMaintenanceRepository @Inject constructor(
    private val taskDefinitionDao: TaskDefinitionDao,
    private val taskInstanceDao: TaskInstanceDao,
    private val equipmentDao: EquipmentDao,
    private val equipmentTypeDao: EquipmentTypeDao,
    private val vesselDao: VesselDao,
    private val preferences: UserPreferencesRepository,
    private val engine: DueEngine,
    private val transaction: TransactionRunner,
    private val clock: AppClock,
) : MaintenanceRepository {

    override fun observeTaskDefinitions(): Flow<List<TaskDefinition>> =
        taskDefinitionDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getTaskDefinition(key: String): TaskDefinition? =
        taskDefinitionDao.getByKey(key)?.toModel()

    override suspend fun upsertTaskDefinition(definition: TaskDefinition) =
        taskDefinitionDao.upsert(definition.toEntity())

    override fun observeTaskInstances(equipmentId: String): Flow<List<TaskInstance>> =
        taskInstanceDao.observeByEquipment(equipmentId).map { rows -> rows.map { it.toModel() } }

    override fun observeOpenInstancesForVessel(vesselId: String): Flow<List<TaskInstance>> =
        taskInstanceDao.observeOpenForVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertInstances(instances: List<TaskInstance>) =
        taskInstanceDao.upsertAll(instances.map { it.toEntity() })

    /**
     * Record a completion and immediately re-derive the item — §11.2. The next occurrence is
     * therefore on screen by the time the completion sheet closes.
     */
    override suspend fun completeTask(
        instanceId: String,
        completedDate: Long,
        completedBy: String?,
        serviceProvider: String?,
        certificateNumber: String?,
        findings: String?,
        conditionAfter: ConditionGrade?,
    ) {
        val equipmentId = taskInstanceDao.getById(instanceId)?.equipmentId ?: return
        transaction {
            taskInstanceDao.complete(
                id = instanceId,
                completedDate = completedDate,
                completedBy = completedBy,
                serviceProvider = serviceProvider,
                certificateNumber = certificateNumber,
                findings = findings,
                conditionAfter = conditionAfter,
                atMillis = clock.nowMillis(),
            )
        }
        recomputeDue(equipmentId)
    }

    override suspend fun recomputeDue(equipmentId: String) {
        val context = loadContext()
        transaction { recompute(equipmentId, context) }
    }

    /**
     * Definitions, the catalogue and the lead time are read once for the whole vessel: a 20-deck
     * ship has hundreds of items and re-reading the (small, whole-table) definition set per item
     * would dominate the work.
     */
    override suspend fun recomputeDueForVessel(vesselId: String) {
        val context = loadContext()
        val ids = equipmentDao.idsForVessel(vesselId)
        transaction {
            ids.forEach { recompute(it, context) }
        }
    }

    private suspend fun recompute(equipmentId: String, context: RecomputeContext) {
        // getById filters soft-deleted rows: a tombstoned item keeps whatever due state it had.
        val item = equipmentDao.getById(equipmentId)?.toModel() ?: return
        val type = equipmentTypeDao.getByKey(item.typeKey)?.toModel() ?: return
        val vessel = vesselDao.getById(item.vesselId)?.toModel()?.let(VesselDueContext::from)
            ?: VesselDueContext()
        val now = clock.nowMillis()
        val result = engine.computeForEquipment(
            equipment = item,
            type = type,
            definitions = context.definitions,
            existingInstances = taskInstanceDao.getByEquipment(equipmentId).map { it.toModel() },
            vessel = vessel,
            todayEpochDay = clock.todayEpochDay(),
            leadTimeDays = context.leadTimeDays,
            nowMillis = now,
        )
        taskInstanceDao.upsertAll(result.instancesToUpsert.map { it.toEntity() })
        equipmentDao.setNextDue(
            id = equipmentId,
            nextDueDate = result.nextDueDate,
            nextDueTaskKey = result.nextDueTaskKey,
            atMillis = now,
        )
    }

    private suspend fun loadContext(): RecomputeContext = RecomputeContext(
        definitions = taskDefinitionDao.getAll().associate { it.key to it.toModel() },
        leadTimeDays = preferences.get().dueLeadTimeDays,
    )

    /** The inputs a whole run of recomputations shares — see [recomputeDueForVessel]. */
    private data class RecomputeContext(
        val definitions: Map<String, TaskDefinition>,
        val leadTimeDays: Int,
    )
}
