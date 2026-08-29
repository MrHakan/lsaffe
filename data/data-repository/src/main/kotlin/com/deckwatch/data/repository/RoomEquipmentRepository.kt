package com.deckwatch.data.repository

import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [EquipmentRepository] — MASTER_PROMPT §6.5, §7.3, §7.5.
 *
 * Every read goes through [EquipmentDao], which filters `deletedAt IS NULL` for us: the soft-delete
 * contract of C10 lives in the DAO, not here. [getEquipment] deliberately uses the deleted-aware
 * query so the 10-second undo can still read the row it has just removed from the deck plan.
 */
@Singleton
class RoomEquipmentRepository @Inject constructor(
    private val equipmentDao: EquipmentDao,
    private val idFactory: IdFactory,
    private val clock: AppClock,
) : EquipmentRepository {

    override fun observeEquipment(vesselId: String): Flow<List<Equipment>> =
        equipmentDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override fun observeEquipmentOnDeck(deckId: String): Flow<List<Equipment>> =
        equipmentDao.observeByDeck(deckId).map { rows -> rows.map { it.toModel() } }

    override fun observeChildren(parentId: String): Flow<List<Equipment>> =
        equipmentDao.observeChildren(parentId).map { rows -> rows.map { it.toModel() } }

    override fun observeUnplaced(vesselId: String): Flow<List<Equipment>> =
        equipmentDao.observeUnplaced(vesselId).map { rows -> rows.map { it.toModel() } }

    /**
     * Sees soft-deleted rows, matching the in-memory fake: undo (§7.3) and import merge (§13.5)
     * both need to read a row they have just tombstoned.
     */
    override suspend fun getEquipment(id: String): Equipment? =
        equipmentDao.getByIdIncludingDeleted(id)?.toModel()

    override suspend fun upsertEquipment(equipment: Equipment) =
        equipmentDao.upsert(equipment.toEntity())

    override suspend fun setCondition(id: String, grade: ConditionGrade, atMillis: Long) =
        equipmentDao.setCondition(id, grade, atMillis)

    override suspend fun move(id: String, deckId: String?, zoneId: String?, posX: Float, posY: Float) =
        equipmentDao.move(id, deckId, zoneId, posX, posY, clock.nowMillis())

    override suspend fun softDelete(id: String, atMillis: Long) =
        equipmentDao.softDelete(id, atMillis)

    override suspend fun undelete(id: String) = equipmentDao.undelete(id, clock.nowMillis())

    /**
     * Duplicate ×N with auto-incremented tags — §7.5.
     *
     * The copies keep the source's position: the officer drags them apart afterwards, which is
     * quicker than guessing an offset for them. Due state is *not* copied — a duplicate has no
     * service history of its own, so `nextDueDate` starts empty and the due engine fills it in on
     * the next recomputation (§11.2).
     */
    override suspend fun duplicate(id: String, count: Int): List<String> {
        if (count <= 0) return emptyList()
        val source = equipmentDao.getById(id)?.toModel() ?: return emptyList()
        val prefix = source.tag.trimEnd { it.isDigit() }
        val firstNumber = equipmentDao.nextTagNumber(source.vesselId, prefix)
        val now = clock.nowMillis()
        val copies = (0 until count).map { offset ->
            source.copy(
                id = idFactory.newId(),
                tag = "$prefix${firstNumber + offset}",
                nextDueDate = null,
                nextDueTaskKey = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        }
        equipmentDao.upsertAll(copies.map { it.toEntity() })
        return copies.map { it.id }
    }

    override suspend fun setCategories(equipmentId: String, categoryIds: List<String>) =
        equipmentDao.setCategories(equipmentId, categoryIds)

    override fun observeCategoryIds(equipmentId: String): Flow<List<String>> =
        equipmentDao.observeCategoryIds(equipmentId)

    override suspend fun nextTagNumber(vesselId: String, prefix: String): Int =
        equipmentDao.nextTagNumber(vesselId, prefix)
}
