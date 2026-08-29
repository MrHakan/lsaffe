package com.deckwatch.core.database.mappers

import com.deckwatch.core.database.entity.EquipmentCategoryXref
import com.deckwatch.core.database.entity.EquipmentTypeEntity
import com.deckwatch.core.database.entity.RegulationCardEntity
import com.deckwatch.core.database.entity.RoundTemplateEntity
import com.deckwatch.core.database.entity.UserNoteEntity
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.RegulationCard
import com.deckwatch.core.model.RoundTemplate
import com.deckwatch.core.model.UserNote

fun EquipmentTypeEntity.toModel(): EquipmentType = EquipmentType(
    typeKey = typeKey,
    group = group,
    subGroup = subGroup,
    nameEn = nameEn,
    nameTr = nameTr,
    symbolKey = symbolKey,
    defaultTagPrefix = defaultTagPrefix,
    attributeSchema = attributeSchema,
    taskKeys = taskKeys,
    regulationRefs = regulationRefs,
    helpTextEn = helpTextEn,
    helpTextTr = helpTextTr,
    commonPscFindings = commonPscFindings,
    isUserDefined = isUserDefined,
)

fun EquipmentType.toEntity(): EquipmentTypeEntity = EquipmentTypeEntity(
    typeKey = typeKey,
    group = group,
    subGroup = subGroup,
    nameEn = nameEn,
    nameTr = nameTr,
    symbolKey = symbolKey,
    defaultTagPrefix = defaultTagPrefix,
    attributeSchema = attributeSchema,
    taskKeys = taskKeys,
    regulationRefs = regulationRefs,
    helpTextEn = helpTextEn,
    helpTextTr = helpTextTr,
    commonPscFindings = commonPscFindings,
    isUserDefined = isUserDefined,
)

fun RegulationCardEntity.toModel(): RegulationCard = RegulationCard(
    refKey = refKey,
    section = section,
    citation = citation,
    title = title,
    what = what,
    howOften = howOften,
    byWhom = byWhom,
    evidence = evidence,
    detailBullets = detailBullets,
    flagNotes = flagNotes,
    appliesToTypeKeys = appliesToTypeKeys,
    sourceRef = sourceRef,
    contentVersion = contentVersion,
    lastReviewed = lastReviewed,
    verificationStatus = verificationStatus,
    summaryTr = summaryTr,
    revisionNote = revisionNote,
)

fun RegulationCard.toEntity(): RegulationCardEntity = RegulationCardEntity(
    refKey = refKey,
    section = section,
    citation = citation,
    title = title,
    what = what,
    howOften = howOften,
    byWhom = byWhom,
    evidence = evidence,
    detailBullets = detailBullets,
    flagNotes = flagNotes,
    appliesToTypeKeys = appliesToTypeKeys,
    sourceRef = sourceRef,
    contentVersion = contentVersion,
    lastReviewed = lastReviewed,
    verificationStatus = verificationStatus,
    summaryTr = summaryTr,
    revisionNote = revisionNote,
)

fun RoundTemplateEntity.toModel(): RoundTemplate = RoundTemplate(
    key = key,
    titleEn = titleEn,
    titleTr = titleTr,
    includesTypeKeys = includesTypeKeys,
    includesGroups = includesGroups,
    descriptionEn = descriptionEn,
)

fun RoundTemplate.toEntity(): RoundTemplateEntity = RoundTemplateEntity(
    key = key,
    titleEn = titleEn,
    titleTr = titleTr,
    includesTypeKeys = includesTypeKeys,
    includesGroups = includesGroups,
    descriptionEn = descriptionEn,
)

fun UserNoteEntity.toModel(): UserNote = UserNote(
    id = id,
    title = title,
    body = body,
    folder = folder,
    regulationRefKey = regulationRefKey,
    equipmentTypeKey = equipmentTypeKey,
    isFavourite = isFavourite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun UserNote.toEntity(): UserNoteEntity = UserNoteEntity(
    id = id,
    title = title,
    body = body,
    folder = folder,
    regulationRefKey = regulationRefKey,
    equipmentTypeKey = equipmentTypeKey,
    isFavourite = isFavourite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * `equipment_category_xref` is a pure join table with no counterpart in `core-model` — the domain
 * expresses the relation as `EquipmentRepository.observeCategoryIds(equipmentId)`. These two
 * helpers are the mapping it needs.
 */
fun EquipmentCategoryXref.toCategoryId(): String = categoryId

fun equipmentCategoryXrefs(equipmentId: String, categoryIds: List<String>): List<EquipmentCategoryXref> =
    categoryIds.map { EquipmentCategoryXref(equipmentId = equipmentId, categoryId = it) }
