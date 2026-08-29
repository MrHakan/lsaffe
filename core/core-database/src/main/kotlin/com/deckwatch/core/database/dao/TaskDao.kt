package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.deckwatch.core.database.entity.TaskDefinitionEntity
import com.deckwatch.core.database.entity.TaskInstanceEntity
import com.deckwatch.core.model.ConditionGrade
import kotlinx.coroutines.flow.Flow

/**
 * Task definitions — MASTER_PROMPT §6.6.
 *
 * `appliesToTypeKeys` is a JSON column, so applicability is resolved in the due engine rather than
 * in SQL; the table is small (tens of rows) and is read whole.
 */
@Dao
interface TaskDefinitionDao {

    @Query("SELECT * FROM task_definitions ORDER BY key")
    fun observeAll(): Flow<List<TaskDefinitionEntity>>

    @Query("SELECT * FROM task_definitions ORDER BY key")
    suspend fun getAll(): List<TaskDefinitionEntity>

    @Query("SELECT * FROM task_definitions WHERE key = :key")
    suspend fun getByKey(key: String): TaskDefinitionEntity?

    @Query("SELECT * FROM task_definitions WHERE key IN (:keys)")
    suspend fun getByKeys(keys: List<String>): List<TaskDefinitionEntity>

    @Upsert
    suspend fun upsert(definition: TaskDefinitionEntity)

    @Upsert
    suspend fun upsertAll(definitions: List<TaskDefinitionEntity>)

    @Query("DELETE FROM task_definitions WHERE key = :key")
    suspend fun deleteByKey(key: String)

    /** Re-seeding on a content-version bump replaces bundled rows and keeps the user's own. */
    @Query("DELETE FROM task_definitions WHERE isUserDefined = 0")
    suspend fun deleteBundled()
}

/**
 * Task instances — MASTER_PROMPT §6.6.
 *
 * "Open" means `PENDING`, `DUE_SOON` or `OVERDUE`: everything the officer still has to do. The
 * three names are spelled as SQL literals rather than bound parameters because [TaskStatus] is
 * stored by `name` (see `DeckWatchTypeConverters`) and a literal keeps the query index-friendly.
 */
@Dao
interface TaskInstanceDao {

    @Query("SELECT * FROM task_instances WHERE equipmentId = :equipmentId ORDER BY dueDate ASC")
    fun observeByEquipment(equipmentId: String): Flow<List<TaskInstanceEntity>>

    @Query(
        """
        SELECT * FROM task_instances
        WHERE equipmentId = :equipmentId AND status IN ('PENDING', 'DUE_SOON', 'OVERDUE')
        ORDER BY dueDate ASC
        """,
    )
    fun observeOpenByEquipment(equipmentId: String): Flow<List<TaskInstanceEntity>>

    /**
     * Every open task on a vessel, resolved through the equipment table because a task instance
     * only knows its equipment. Soft-deleted equipment drops out with it.
     */
    @Query(
        """
        SELECT ti.* FROM task_instances ti
        INNER JOIN equipment e ON e.id = ti.equipmentId
        WHERE e.vesselId = :vesselId AND e.deletedAt IS NULL
          AND ti.status IN ('PENDING', 'DUE_SOON', 'OVERDUE')
        ORDER BY ti.dueDate ASC
        """,
    )
    fun observeOpenForVessel(vesselId: String): Flow<List<TaskInstanceEntity>>

    /** The Due tab's segments — §12. [onOrBeforeEpochDay] is epoch-days. */
    @Query(
        """
        SELECT ti.* FROM task_instances ti
        INNER JOIN equipment e ON e.id = ti.equipmentId
        WHERE e.vesselId = :vesselId AND e.deletedAt IS NULL
          AND ti.status IN ('PENDING', 'DUE_SOON', 'OVERDUE')
          AND ti.dueDate <= :onOrBeforeEpochDay
        ORDER BY ti.dueDate ASC
        """,
    )
    fun observeOpenForVesselDueOnOrBefore(
        vesselId: String,
        onOrBeforeEpochDay: Long,
    ): Flow<List<TaskInstanceEntity>>

    @Query("SELECT * FROM task_instances WHERE id = :id")
    suspend fun getById(id: String): TaskInstanceEntity?

    @Query("SELECT * FROM task_instances WHERE equipmentId = :equipmentId ORDER BY dueDate ASC")
    suspend fun getByEquipment(equipmentId: String): List<TaskInstanceEntity>

    /** Feeds the denormalised `equipment.nextDueDate` / `nextDueTaskKey` — §11.1 step 5. */
    @Query(
        """
        SELECT * FROM task_instances
        WHERE equipmentId = :equipmentId AND status IN ('PENDING', 'DUE_SOON', 'OVERDUE')
        ORDER BY dueDate ASC LIMIT 1
        """,
    )
    suspend fun getSoonestOpen(equipmentId: String): TaskInstanceEntity?

    @Query("SELECT * FROM task_instances WHERE equipmentId = :equipmentId AND taskKey = :taskKey ORDER BY dueDate DESC")
    suspend fun getForTask(equipmentId: String, taskKey: String): List<TaskInstanceEntity>

    /** Most recent completion of one task on one item — the due engine's anchor date. */
    @Query(
        """
        SELECT * FROM task_instances
        WHERE equipmentId = :equipmentId AND taskKey = :taskKey AND completedDate IS NOT NULL
        ORDER BY completedDate DESC LIMIT 1
        """,
    )
    suspend fun getLastCompleted(equipmentId: String, taskKey: String): TaskInstanceEntity?

    @Upsert
    suspend fun upsert(instance: TaskInstanceEntity)

    @Upsert
    suspend fun upsertAll(instances: List<TaskInstanceEntity>)

    @Query(
        """
        UPDATE task_instances SET
            status = 'DONE',
            completedDate = :completedDate,
            completedBy = :completedBy,
            serviceProvider = :serviceProvider,
            certificateNumber = :certificateNumber,
            findings = :findings,
            conditionAfter = :conditionAfter,
            updatedAt = :atMillis
        WHERE id = :id
        """,
    )
    suspend fun complete(
        id: String,
        completedDate: Long,
        completedBy: String?,
        serviceProvider: String?,
        certificateNumber: String?,
        findings: String?,
        conditionAfter: ConditionGrade?,
        atMillis: Long,
    )

    @Query("DELETE FROM task_instances WHERE id = :id")
    suspend fun deleteById(id: String)

    /** The engine re-derives an item's open instances from scratch; completed history stays. */
    @Query("DELETE FROM task_instances WHERE equipmentId = :equipmentId AND status IN ('PENDING', 'DUE_SOON', 'OVERDUE')")
    suspend fun deleteOpenForEquipment(equipmentId: String)
}
