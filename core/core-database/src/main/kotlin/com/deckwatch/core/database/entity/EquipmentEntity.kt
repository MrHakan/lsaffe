package com.deckwatch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.StatusFlag

/**
 * The core equipment record — MASTER_PROMPT §6.5.
 *
 * Deletion is **soft** ([deletedAt] non-null): C10 requires every destructive action to be
 * undoable for 10 seconds, and §13.5 requires a deletion on one device to propagate on import
 * rather than resurrect. Every default query in [com.deckwatch.core.database.dao.EquipmentDao]
 * therefore filters `deletedAt IS NULL`.
 *
 * [nextDueDate] / [nextDueTaskKey] are denormalised by the due engine (§11.1 step 5) so the plan
 * view can colour 600 markers without a join.
 */
@Entity(
    tableName = "equipment",
    indices = [
        Index("vesselId"),
        Index("deckId"),
        Index("zoneId"),
        Index("typeKey"),
        Index("nextDueDate"),
    ],
)
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    /** null == "unplaced": the item lives in an inbox until it is positioned on a deck. */
    val deckId: String?,
    val zoneId: String?,
    /** Set for equipment mounted inside/on other equipment (a lifeboat's extinguisher, an HRU). */
    val parentId: String?,
    /** FK into the bundled equipment type catalogue — §9. */
    val typeKey: String,
    /** FK into the symbol library — §10. */
    val symbolKey: String,
    /** The ship's own identifier — "FE-UD-07", "LB No.1". */
    val tag: String,
    val name: String?,
    val location: String?,
    /** 0..1 within the deck plan. */
    val posX: Float,
    val posY: Float,
    val rotationDeg: Float,
    val makerName: String?,
    val modelName: String?,
    val serialNumber: String?,
    /** MED / wheelmark / USCG approval number. */
    val typeApprovalNumber: String?,
    /** Epoch-days. */
    val manufactureDate: Long?,
    /** Epoch-days. */
    val installedDate: Long?,
    val quantity: Int,
    val condition: ConditionGrade,
    /** Epoch-millis: when the grade was set, not a due date. */
    val conditionSetAt: Long?,
    val statusFlag: StatusFlag,
    /** Dynamic, type-specific attribute values as a JSON object — §9.3. */
    val attributesJson: String,
    /** Epoch-days. Denormalised soonest due, recomputed by the due engine. */
    val nextDueDate: Long?,
    val nextDueTaskKey: String?,
    val photoUris: List<String>,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Epoch-millis. Non-null == soft-deleted; kept for undo and for merge on import. */
    val deletedAt: Long?,
)
