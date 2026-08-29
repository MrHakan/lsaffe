package com.deckwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Equipment(
    val id: String,
    val vesselId: String,
    /** null == "unplaced", lives in an inbox until positioned. */
    val deckId: String? = null,
    val zoneId: String? = null,
    /** For equipment mounted inside/on other equipment (lifeboat's extinguisher, liferaft's HRU). */
    val parentId: String? = null,
    val typeKey: String,
    val symbolKey: String,
    /** Ship's own identifier — "FE-UD-07", "LB No.1". */
    val tag: String,
    val name: String? = null,
    val location: String? = null,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val rotationDeg: Float = 0f,
    val makerName: String? = null,
    val modelName: String? = null,
    val serialNumber: String? = null,
    val typeApprovalNumber: String? = null,
    /** Epoch-days. */
    val manufactureDate: Long? = null,
    val installedDate: Long? = null,
    val quantity: Int = 1,
    val condition: ConditionGrade = ConditionGrade.NOT_CHECKED,
    val conditionSetAt: Long? = null,
    val statusFlag: StatusFlag = StatusFlag.IN_SERVICE,
    /** Dynamic, type-specific attribute values as JSON — §9.3. */
    val attributesJson: String = "{}",
    /** Denormalised soonest due (epoch-days), recomputed by the due engine. */
    val nextDueDate: Long? = null,
    val nextDueTaskKey: String? = null,
    val photoUris: List<String> = emptyList(),
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Soft delete, for undo + merge on import. */
    val deletedAt: Long? = null,
)
