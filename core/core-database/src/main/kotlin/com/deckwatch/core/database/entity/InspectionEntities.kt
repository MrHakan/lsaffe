package com.deckwatch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity

/** One inspection round — MASTER_PROMPT §6.7. Sweep mode writes one of these automatically. */
@Entity(tableName = "rounds")
data class RoundEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    /** "WEEKLY_LSA", "MONTHLY_FFE", "PRE_ARRIVAL_PSC", or a custom template key. */
    val templateKey: String,
    val title: String,
    /** Epoch-millis. */
    val startedAt: Long,
    val completedAt: Long?,
    val performedBy: String,
    val itemCount: Int,
    val doneCount: Int,
    val deficiencyCount: Int,
    val notes: String?,
)

/** One graded item within a round — MASTER_PROMPT §6.7. */
@Entity(tableName = "round_items", indices = [Index("roundId"), Index("equipmentId")])
data class RoundItemEntity(
    @PrimaryKey val id: String,
    val roundId: String,
    val equipmentId: String,
    /** Epoch-millis. null == not yet checked on this round. */
    val checkedAt: Long?,
    val condition: ConditionGrade?,
    val remark: String?,
    val photoUris: List<String>,
)

/** A raised deficiency — MASTER_PROMPT §6.8. */
@Entity(tableName = "deficiencies", indices = [Index("equipmentId"), Index("status")])
data class DeficiencyEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    /** null for a deficiency that is not against one specific item. */
    val equipmentId: String?,
    /** Epoch-days. */
    val raisedDate: Long,
    val raisedBy: String,
    val severity: Severity,
    val title: String,
    val description: String,
    val correctiveAction: String?,
    /** Epoch-days. */
    val targetDate: Long?,
    val closedDate: Long?,
    val closedBy: String?,
    val status: DeficiencyStatus,
    val sparePartRequired: String?,
    val photoUris: List<String>,
)
