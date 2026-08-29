package com.deckwatch.core.database.mappers

import com.deckwatch.core.database.entity.DeficiencyEntity
import com.deckwatch.core.database.entity.RoundEntity
import com.deckwatch.core.database.entity.RoundItemEntity
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem

fun RoundEntity.toModel(): Round = Round(
    id = id,
    vesselId = vesselId,
    templateKey = templateKey,
    title = title,
    startedAt = startedAt,
    completedAt = completedAt,
    performedBy = performedBy,
    itemCount = itemCount,
    doneCount = doneCount,
    deficiencyCount = deficiencyCount,
    notes = notes,
)

fun Round.toEntity(): RoundEntity = RoundEntity(
    id = id,
    vesselId = vesselId,
    templateKey = templateKey,
    title = title,
    startedAt = startedAt,
    completedAt = completedAt,
    performedBy = performedBy,
    itemCount = itemCount,
    doneCount = doneCount,
    deficiencyCount = deficiencyCount,
    notes = notes,
)

fun RoundItemEntity.toModel(): RoundItem = RoundItem(
    id = id,
    roundId = roundId,
    equipmentId = equipmentId,
    checkedAt = checkedAt,
    condition = condition,
    remark = remark,
    photoUris = photoUris,
)

fun RoundItem.toEntity(): RoundItemEntity = RoundItemEntity(
    id = id,
    roundId = roundId,
    equipmentId = equipmentId,
    checkedAt = checkedAt,
    condition = condition,
    remark = remark,
    photoUris = photoUris,
)

fun DeficiencyEntity.toModel(): Deficiency = Deficiency(
    id = id,
    vesselId = vesselId,
    equipmentId = equipmentId,
    raisedDate = raisedDate,
    raisedBy = raisedBy,
    severity = severity,
    title = title,
    description = description,
    correctiveAction = correctiveAction,
    targetDate = targetDate,
    closedDate = closedDate,
    closedBy = closedBy,
    status = status,
    sparePartRequired = sparePartRequired,
    photoUris = photoUris,
)

fun Deficiency.toEntity(): DeficiencyEntity = DeficiencyEntity(
    id = id,
    vesselId = vesselId,
    equipmentId = equipmentId,
    raisedDate = raisedDate,
    raisedBy = raisedBy,
    severity = severity,
    title = title,
    description = description,
    correctiveAction = correctiveAction,
    targetDate = targetDate,
    closedDate = closedDate,
    closedBy = closedBy,
    status = status,
    sparePartRequired = sparePartRequired,
    photoUris = photoUris,
)
