package com.deckwatch.core.database.mappers

import com.deckwatch.core.database.entity.TaskDefinitionEntity
import com.deckwatch.core.database.entity.TaskInstanceEntity
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance

fun TaskDefinitionEntity.toModel(): TaskDefinition = TaskDefinition(
    key = key,
    appliesToTypeKeys = appliesToTypeKeys,
    titleEn = titleEn,
    titleTr = titleTr,
    descriptionEn = descriptionEn,
    intervalKind = intervalKind,
    intervalMonths = intervalMonths,
    toleranceDaysBefore = toleranceDaysBefore,
    toleranceDaysAfter = toleranceDaysAfter,
    performedBy = performedBy,
    evidenceRequired = evidenceRequired,
    regulationRefs = regulationRefs,
    flagOverrides = flagOverrides,
    sourceRef = sourceRef,
    verificationStatus = verificationStatus,
    lastReviewed = lastReviewed,
    isUserDefined = isUserDefined,
)

fun TaskDefinition.toEntity(): TaskDefinitionEntity = TaskDefinitionEntity(
    key = key,
    appliesToTypeKeys = appliesToTypeKeys,
    titleEn = titleEn,
    titleTr = titleTr,
    descriptionEn = descriptionEn,
    intervalKind = intervalKind,
    intervalMonths = intervalMonths,
    toleranceDaysBefore = toleranceDaysBefore,
    toleranceDaysAfter = toleranceDaysAfter,
    performedBy = performedBy,
    evidenceRequired = evidenceRequired,
    regulationRefs = regulationRefs,
    flagOverrides = flagOverrides,
    sourceRef = sourceRef,
    verificationStatus = verificationStatus,
    lastReviewed = lastReviewed,
    isUserDefined = isUserDefined,
)

fun TaskInstanceEntity.toModel(): TaskInstance = TaskInstance(
    id = id,
    equipmentId = equipmentId,
    taskKey = taskKey,
    dueDate = dueDate,
    windowOpens = windowOpens,
    windowCloses = windowCloses,
    status = status,
    completedDate = completedDate,
    completedBy = completedBy,
    serviceProvider = serviceProvider,
    certificateNumber = certificateNumber,
    findings = findings,
    conditionAfter = conditionAfter,
    photoUris = photoUris,
    attachmentUris = attachmentUris,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TaskInstance.toEntity(): TaskInstanceEntity = TaskInstanceEntity(
    id = id,
    equipmentId = equipmentId,
    taskKey = taskKey,
    dueDate = dueDate,
    windowOpens = windowOpens,
    windowCloses = windowCloses,
    status = status,
    completedDate = completedDate,
    completedBy = completedBy,
    serviceProvider = serviceProvider,
    certificateNumber = certificateNumber,
    findings = findings,
    conditionAfter = conditionAfter,
    photoUris = photoUris,
    attachmentUris = attachmentUris,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
