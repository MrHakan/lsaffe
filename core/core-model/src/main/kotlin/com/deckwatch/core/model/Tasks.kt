package com.deckwatch.core.model

import kotlinx.serialization.Serializable

/** Interval rules live in data, not code — §6.6. */
@Serializable
data class TaskDefinition(
    val key: String,
    val appliesToTypeKeys: List<String>,
    val titleEn: String,
    val titleTr: String,
    val descriptionEn: String = "",
    val intervalKind: IntervalKind,
    val intervalMonths: Int? = null,
    val toleranceDaysBefore: Int = 0,
    val toleranceDaysAfter: Int = 0,
    val performedBy: PerformedBy,
    val evidenceRequired: List<String> = emptyList(),
    val regulationRefs: List<String> = emptyList(),
    /** Flag code -> short note on the difference. */
    val flagOverrides: Map<String, String>? = null,
    val sourceRef: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val lastReviewed: String = "",
    val isUserDefined: Boolean = false,
)

@Serializable
data class TaskInstance(
    val id: String,
    val equipmentId: String,
    val taskKey: String,
    /** Epoch-days. */
    val dueDate: Long,
    val windowOpens: Long,
    val windowCloses: Long,
    val status: TaskStatus,
    val completedDate: Long? = null,
    val completedBy: String? = null,
    val serviceProvider: String? = null,
    val certificateNumber: String? = null,
    val findings: String? = null,
    val conditionAfter: ConditionGrade? = null,
    val photoUris: List<String> = emptyList(),
    val attachmentUris: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Round(
    val id: String,
    val vesselId: String,
    val templateKey: String,
    val title: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val performedBy: String = "",
    val itemCount: Int = 0,
    val doneCount: Int = 0,
    val deficiencyCount: Int = 0,
    val notes: String? = null,
)

@Serializable
data class RoundItem(
    val id: String,
    val roundId: String,
    val equipmentId: String,
    val checkedAt: Long? = null,
    val condition: ConditionGrade? = null,
    val remark: String? = null,
    val photoUris: List<String> = emptyList(),
)

@Serializable
data class RoundTemplate(
    val key: String,
    val titleEn: String,
    val titleTr: String,
    /** Equipment type keys (or group names) included in this round. */
    val includesTypeKeys: List<String> = emptyList(),
    val includesGroups: List<EquipmentGroup> = emptyList(),
    val descriptionEn: String = "",
)

@Serializable
data class Deficiency(
    val id: String,
    val vesselId: String,
    val equipmentId: String? = null,
    /** Epoch-days. */
    val raisedDate: Long,
    val raisedBy: String = "",
    val severity: Severity,
    val title: String,
    val description: String = "",
    val correctiveAction: String? = null,
    val targetDate: Long? = null,
    val closedDate: Long? = null,
    val closedBy: String? = null,
    val status: DeficiencyStatus = DeficiencyStatus.OPEN,
    val sparePartRequired: String? = null,
    val photoUris: List<String> = emptyList(),
)
