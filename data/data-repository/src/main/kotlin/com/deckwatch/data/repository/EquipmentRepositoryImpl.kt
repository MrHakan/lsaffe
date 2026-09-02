package com.deckwatch.data.repository

import androidx.room.withTransaction
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.EquipmentRepository
import com.deckwatch.core.database.DeckWatchDatabase
import com.deckwatch.core.database.dao.EquipmentDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Equipment
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Equipment records — MASTER_PROMPT §6.5, §7.5.
 *
 * **Deletion is soft.** [softDelete] stamps `deletedAt`, [undelete] clears it, and every read here
 * comes from a DAO query that filters `deletedAt IS NULL`; this is what makes the 10-second undo of
 * C10 possible and what lets a deletion propagate on import (§13.5) instead of resurrecting.
 *
 * **Duplication** (§7.5's "duplicate ×N", for the deck with fourteen identical extinguishers)
 * copies the record, gives each copy the next free tag number for the source's tag pattern, and
 * writes them in one transaction so a half-finished batch can never be observed. A copy is a
 * *new physical item*, so its serial number, photographs and denormalised due state are cleared —
 * the due engine fills the last of those in on the next recomputation.
 */
@Singleton
class EquipmentRepositoryImpl @Inject constructor(
    private val database: DeckWatchDatabase,
    private val equipmentDao: EquipmentDao,
    private val dispatchers: DispatcherProvider,
    private val time: TimeSource,
) : EquipmentRepository {

    override fun observeEquipment(vesselId: String): Flow<List<Equipment>> =
        equipmentDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override fun observeEquipmentOnDeck(deckId: String): Flow<List<Equipment>> =
        equipmentDao.observeByDeck(deckId).map { rows -> rows.map { it.toModel() } }

    override fun observeChildren(parentId: String): Flow<List<Equipment>> =
        equipmentDao.observeChildren(parentId).map { rows -> rows.map { it.toModel() } }

    override fun observeUnplaced(vesselId: String): Flow<List<Equipment>> =
        equipmentDao.observeUnplaced(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getEquipment(id: String): Equipment? = withContext(dispatchers.io) {
        equipmentDao.getById(id)?.toModel()
    }

    override suspend fun upsertEquipment(equipment: Equipment) = withContext(dispatchers.io) {
        equipmentDao.upsert(equipment.toEntity())
    }

    override suspend fun setCondition(id: String, grade: ConditionGrade, atMillis: Long) =
        withContext(dispatchers.io) { equipmentDao.setCondition(id, grade, atMillis) }

    /**
     * Move an item between decks and zones, or off the plan entirely: passing a null [deckId]
     * returns it to the unplaced inbox (§6.5).
     */
    override suspend fun move(id: String, deckId: String?, zoneId: String?, posX: Float, posY: Float) =
        withContext(dispatchers.io) {
            equipmentDao.move(
                id = id,
                deckId = deckId,
                zoneId = zoneId,
                posX = posX,
                posY = posY,
                atMillis = time.nowMillis(),
            )
        }

    override suspend fun softDelete(id: String, atMillis: Long) = withContext(dispatchers.io) {
        equipmentDao.softDelete(id, atMillis)
    }

    override suspend fun undelete(id: String) = withContext(dispatchers.io) {
        equipmentDao.undelete(id, time.nowMillis())
    }

    override suspend fun duplicate(id: String, count: Int): List<String> =
        withContext(dispatchers.io) {
            if (count <= 0) return@withContext emptyList()
            database.withTransaction {
                val source = equipmentDao.getById(id)?.toModel() ?: return@withTransaction emptyList()
                val pattern = TagPattern.of(source.tag)
                var next = equipmentDao.nextTagNumber(source.vesselId, pattern.prefix)
                val now = time.nowMillis()
                val copies = ArrayList<Equipment>(count)
                repeat(count) {
                    copies += source.copy(
                        id = UUID.randomUUID().toString(),
                        tag = pattern.render(next),
                        serialNumber = null,
                        photoUris = emptyList(),
                        nextDueDate = null,
                        nextDueTaskKey = null,
                        conditionSetAt = null,
                        condition = ConditionGrade.NOT_CHECKED,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    )
                    next++
                }
                equipmentDao.upsertAll(copies.map { it.toEntity() })
                copies.map { it.id }
            }
        }

    override suspend fun setCategories(equipmentId: String, categoryIds: List<String>) =
        withContext(dispatchers.io) { equipmentDao.setCategories(equipmentId, categoryIds) }

    override fun observeCategoryIds(equipmentId: String): Flow<List<String>> =
        equipmentDao.observeCategoryIds(equipmentId)

    override suspend fun nextTagNumber(vesselId: String, prefix: String): Int =
        withContext(dispatchers.io) { equipmentDao.nextTagNumber(vesselId, prefix) }
}
