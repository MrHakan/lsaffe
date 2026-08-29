package com.deckwatch.core.database.mappers

import com.deckwatch.core.database.entity.CategoryEntity
import com.deckwatch.core.database.entity.DeckEntity
import com.deckwatch.core.database.entity.VesselEntity
import com.deckwatch.core.database.entity.ZoneEntity
import com.deckwatch.core.model.Category
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone

fun VesselEntity.toModel(): Vessel = Vessel(
    id = id,
    name = name,
    imoNumber = imoNumber,
    callSign = callSign,
    mmsi = mmsi,
    flag = flag,
    flagOtherName = flagOtherName,
    classSociety = classSociety,
    vesselType = vesselType,
    grossTonnage = grossTonnage,
    buildDate = buildDate,
    safetyEquipmentCertExpiry = safetyEquipmentCertExpiry,
    lastAnnualSurveyDate = lastAnnualSurveyDate,
    nextDrydockDate = nextDrydockDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Vessel.toEntity(): VesselEntity = VesselEntity(
    id = id,
    name = name,
    imoNumber = imoNumber,
    callSign = callSign,
    mmsi = mmsi,
    flag = flag,
    flagOtherName = flagOtherName,
    classSociety = classSociety,
    vesselType = vesselType,
    grossTonnage = grossTonnage,
    buildDate = buildDate,
    safetyEquipmentCertExpiry = safetyEquipmentCertExpiry,
    lastAnnualSurveyDate = lastAnnualSurveyDate,
    nextDrydockDate = nextDrydockDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DeckEntity.toModel(): Deck = Deck(
    id = id,
    vesselId = vesselId,
    name = name,
    shortCode = shortCode,
    levelIndex = levelIndex,
    plan = plan,
    colorTint = colorTint,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    vesselId = vesselId,
    name = name,
    shortCode = shortCode,
    levelIndex = levelIndex,
    plan = plan,
    colorTint = colorTint,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ZoneEntity.toModel(): Zone = Zone(
    id = id,
    deckId = deckId,
    name = name,
    polygon = polygon,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
)

fun Zone.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    deckId = deckId,
    name = name,
    polygon = polygon,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
)

fun CategoryEntity.toModel(): Category = Category(
    id = id,
    vesselId = vesselId,
    name = name,
    colorArgb = colorArgb,
    iconKey = iconKey,
    sortOrder = sortOrder,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    vesselId = vesselId,
    name = name,
    colorArgb = colorArgb,
    iconKey = iconKey,
    sortOrder = sortOrder,
)
