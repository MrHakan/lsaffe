package com.deckwatch.feature.inspection

import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import kotlinx.coroutines.flow.Flow

/**
 * A [MaintenanceRepository] that delegates to the real in-memory fake and records the calls the Due
 * tab is contractually required to make — the completion itself and the due-engine recomputation
 * that must follow it (§11.2).
 */
internal class RecordingMaintenanceRepository(
    private val delegate: MaintenanceRepository,
) : MaintenanceRepository by delegate {

    data class Completion(
        val instanceId: String,
        val completedDate: Long,
        val completedBy: String?,
        val serviceProvider: String?,
        val certificateNumber: String?,
        val findings: String?,
        val conditionAfter: ConditionGrade?,
    )

    val completions = mutableListOf<Completion>()
    val recomputedEquipmentIds = mutableListOf<String>()
    val upsertedInstances = mutableListOf<TaskInstance>()

    override fun observeTaskDefinitions(): Flow<List<TaskDefinition>> = delegate.observeTaskDefinitions()

    override suspend fun completeTask(
        instanceId: String,
        completedDate: Long,
        completedBy: String?,
        serviceProvider: String?,
        certificateNumber: String?,
        findings: String?,
        conditionAfter: ConditionGrade?,
    ) {
        completions += Completion(
            instanceId = instanceId,
            completedDate = completedDate,
            completedBy = completedBy,
            serviceProvider = serviceProvider,
            certificateNumber = certificateNumber,
            findings = findings,
            conditionAfter = conditionAfter,
        )
        delegate.completeTask(
            instanceId = instanceId,
            completedDate = completedDate,
            completedBy = completedBy,
            serviceProvider = serviceProvider,
            certificateNumber = certificateNumber,
            findings = findings,
            conditionAfter = conditionAfter,
        )
    }

    override suspend fun upsertInstances(instances: List<TaskInstance>) {
        upsertedInstances += instances
        delegate.upsertInstances(instances)
    }

    override suspend fun recomputeDue(equipmentId: String) {
        recomputedEquipmentIds += equipmentId
        delegate.recomputeDue(equipmentId)
    }
}
