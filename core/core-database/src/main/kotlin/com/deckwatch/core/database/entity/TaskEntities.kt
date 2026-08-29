package com.deckwatch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.model.VerificationStatus

/**
 * A maintenance task definition — MASTER_PROMPT §6.6.
 *
 * The interval rules live in **data**, not code, so a wrong interval can be corrected by a content
 * bump instead of a release. [sourceRef], [verificationStatus] and [lastReviewed] carry the
 * content-accuracy contract of §8.5 / §11.4: an unconfirmed figure ships as `UNVERIFIED` and the
 * UI renders the amber "verify against the current instrument" strip.
 */
@Entity(tableName = "task_definitions")
data class TaskDefinitionEntity(
    /** e.g. "FE_MONTHLY_INSPECTION", "LB_ANNUAL_THOROUGH_EXAM". */
    @PrimaryKey val key: String,
    val appliesToTypeKeys: List<String>,
    val titleEn: String,
    val titleTr: String,
    val descriptionEn: String,
    val intervalKind: IntervalKind,
    val intervalMonths: Int?,
    /** e.g. 90 for the ±3-month HSSC window. */
    val toleranceDaysBefore: Int,
    val toleranceDaysAfter: Int,
    val performedBy: PerformedBy,
    val evidenceRequired: List<String>,
    /** Reference keys into the regulation cards — §8. */
    val regulationRefs: List<String>,
    /** Flag code -> short note on the difference — §11.5. */
    val flagOverrides: Map<String, String>?,
    val sourceRef: String,
    val verificationStatus: VerificationStatus,
    /** ISO date the statement was last reviewed against the instrument. */
    val lastReviewed: String,
    val isUserDefined: Boolean,
)

/**
 * One scheduled or completed occurrence of a task — MASTER_PROMPT §6.6.
 *
 * [dueDate], [windowOpens] and [windowCloses] are epoch-days so a tolerance window never drifts
 * across a date boundary when the vessel changes timezone.
 */
@Entity(
    tableName = "task_instances",
    indices = [Index("equipmentId"), Index("dueDate"), Index("status")],
)
data class TaskInstanceEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val taskKey: String,
    /** Epoch-days. */
    val dueDate: Long,
    val windowOpens: Long,
    val windowCloses: Long,
    val status: TaskStatus,
    /** Epoch-days. */
    val completedDate: Long?,
    /** Free text: rank/name, or the service company. */
    val completedBy: String?,
    val serviceProvider: String?,
    val certificateNumber: String?,
    val findings: String?,
    val conditionAfter: ConditionGrade?,
    val photoUris: List<String>,
    val attachmentUris: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)
