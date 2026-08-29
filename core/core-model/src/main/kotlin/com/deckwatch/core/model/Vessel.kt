package com.deckwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Vessel(
    val id: String,
    val name: String,
    val imoNumber: String? = null,
    val callSign: String? = null,
    val mmsi: String? = null,
    val flag: FlagState = FlagState.OTHER,
    val flagOtherName: String? = null,
    val classSociety: ClassSociety? = null,
    val vesselType: VesselType = VesselType.OTHER,
    val grossTonnage: Int? = null,
    /** Epoch-days. */
    val buildDate: Long? = null,
    /** Epoch-days — drives "due before next survey". */
    val safetyEquipmentCertExpiry: Long? = null,
    val lastAnnualSurveyDate: Long? = null,
    val nextDrydockDate: Long? = null,
    val isActive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Deck(
    val id: String,
    val vesselId: String,
    val name: String,
    val shortCode: String? = null,
    val levelIndex: Int,
    val plan: DeckPlan,
    val colorTint: Int? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Zone(
    val id: String,
    val deckId: String,
    val name: String,
    val polygon: List<PlanPoint>,
    val colorArgb: Int,
    val sortOrder: Int,
)

@Serializable
data class Category(
    val id: String,
    /** null == global category available on every vessel. */
    val vesselId: String? = null,
    val name: String,
    val colorArgb: Int,
    val iconKey: String? = null,
    val sortOrder: Int,
)
