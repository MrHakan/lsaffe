package com.deckwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.deckwatch.core.model.ClassSociety
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.VesselType

/**
 * A vessel — MASTER_PROMPT §6.1.
 *
 * All dates here are **epoch-days**, not epoch-millis: a survey or certificate date must never
 * shift because the phone changed timezone. Only [createdAt] / [updatedAt] are epoch-millis.
 */
@Entity(tableName = "vessels")
data class VesselEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 7 digits, validated with the IMO check-digit algorithm before it reaches the DAO. */
    val imoNumber: String?,
    val callSign: String?,
    val mmsi: String?,
    val flag: FlagState,
    val flagOtherName: String?,
    val classSociety: ClassSociety?,
    val vesselType: VesselType,
    val grossTonnage: Int?,
    /** Epoch-days. Drives keel-laid-based rule applicability. */
    val buildDate: Long?,
    /** Epoch-days. Drives "due before next survey". */
    val safetyEquipmentCertExpiry: Long?,
    val lastAnnualSurveyDate: Long?,
    val nextDrydockDate: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One deck of the vertical stack — MASTER_PROMPT §6.2.
 *
 * [levelIndex] is deliberately sparse (step of 10) so a deck can be inserted between two others
 * without renumbering, and it is unique per vessel so two decks can never occupy one level.
 * Deleting a vessel cascades to its decks.
 */
@Entity(
    tableName = "decks",
    foreignKeys = [
        ForeignKey(
            entity = VesselEntity::class,
            parentColumns = ["id"],
            childColumns = ["vesselId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vesselId"), Index(value = ["vesselId", "levelIndex"], unique = true)],
)
data class DeckEntity(
    @PrimaryKey val id: String,
    val vesselId: String,
    val name: String,
    /** "UD", "A", "BR" — shown on the stack spine. */
    val shortCode: String?,
    /** 0 = the first deck created. Positive above, negative below. Not contiguous. */
    val levelIndex: Int,
    val plan: DeckPlan,
    /** ARGB, user-assignable per deck. */
    val colorTint: Int?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A spatial zone drawn on a deck plan — MASTER_PROMPT §6.4. */
@Entity(tableName = "zones", indices = [Index("deckId")])
data class ZoneEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val name: String,
    val polygon: List<PlanPoint>,
    val colorArgb: Int,
    val sortOrder: Int,
)

/** A logical category applied to equipment regardless of location — MASTER_PROMPT §6.4. */
@Entity(tableName = "categories", indices = [Index("vesselId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    /** null == global category, available on every vessel. */
    val vesselId: String?,
    val name: String,
    val colorArgb: Int,
    val iconKey: String?,
    val sortOrder: Int,
)

/** Many-to-many join between equipment and logical categories — MASTER_PROMPT §6.4. */
@Entity(tableName = "equipment_category_xref", primaryKeys = ["equipmentId", "categoryId"])
data class EquipmentCategoryXref(
    val equipmentId: String,
    val categoryId: String,
)
