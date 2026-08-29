package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.deckwatch.core.database.entity.EquipmentCategoryXref
import com.deckwatch.core.database.entity.EquipmentEntity
import com.deckwatch.core.database.mappers.equipmentCategoryXrefs
import com.deckwatch.core.model.ConditionGrade
import kotlinx.coroutines.flow.Flow

/**
 * Serves `EquipmentRepository` — MASTER_PROMPT §6.5.
 *
 * **Soft-delete contract:** every `observe*` and `getById` here filters `deletedAt IS NULL`. The
 * only reader that sees deleted rows is [getByIdIncludingDeleted], which undo (§7.3) and import
 * merge (§13.5) need. Adding a query that forgets the filter would resurrect deleted equipment on
 * the deck plan, so keep the filter in every new query.
 */
@Dao
abstract class EquipmentDao {

    @Query(
        """
        SELECT * FROM equipment
        WHERE vesselId = :vesselId AND deletedAt IS NULL
        ORDER BY tag COLLATE NOCASE
        """,
    )
    abstract fun observeByVessel(vesselId: String): Flow<List<EquipmentEntity>>

    @Query(
        """
        SELECT * FROM equipment
        WHERE deckId = :deckId AND deletedAt IS NULL
        ORDER BY tag COLLATE NOCASE
        """,
    )
    abstract fun observeByDeck(deckId: String): Flow<List<EquipmentEntity>>

    @Query(
        """
        SELECT * FROM equipment
        WHERE zoneId = :zoneId AND deletedAt IS NULL
        ORDER BY tag COLLATE NOCASE
        """,
    )
    abstract fun observeByZone(zoneId: String): Flow<List<EquipmentEntity>>

    /** Sub-components: a lifeboat's own extinguisher, a liferaft's HRU — §7.6. */
    @Query(
        """
        SELECT * FROM equipment
        WHERE parentId = :parentId AND deletedAt IS NULL
        ORDER BY tag COLLATE NOCASE
        """,
    )
    abstract fun observeChildren(parentId: String): Flow<List<EquipmentEntity>>

    /** The "unplaced" inbox: created but not yet positioned on a deck — §6.5. */
    @Query(
        """
        SELECT * FROM equipment
        WHERE vesselId = :vesselId AND deckId IS NULL AND deletedAt IS NULL
        ORDER BY tag COLLATE NOCASE
        """,
    )
    abstract fun observeUnplaced(vesselId: String): Flow<List<EquipmentEntity>>

    @Query(
        """
        SELECT e.* FROM equipment e
        INNER JOIN equipment_category_xref x ON x.equipmentId = e.id
        WHERE x.categoryId = :categoryId AND e.deletedAt IS NULL
        ORDER BY e.tag COLLATE NOCASE
        """,
    )
    abstract fun observeByCategory(categoryId: String): Flow<List<EquipmentEntity>>

    /** Feeds the Due tab's "before next survey" segment — §12. Dates are epoch-days. */
    @Query(
        """
        SELECT * FROM equipment
        WHERE vesselId = :vesselId AND deletedAt IS NULL
          AND nextDueDate IS NOT NULL AND nextDueDate <= :onOrBeforeEpochDay
        ORDER BY nextDueDate ASC
        """,
    )
    abstract fun observeDueOnOrBefore(vesselId: String, onOrBeforeEpochDay: Long): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getById(id: String): EquipmentEntity?

    /** Sees soft-deleted rows. Needed by undo (§7.3) and by import merge (§13.5). */
    @Query("SELECT * FROM equipment WHERE id = :id")
    abstract suspend fun getByIdIncludingDeleted(id: String): EquipmentEntity?

    @Query("SELECT * FROM equipment WHERE vesselId = :vesselId AND deletedAt IS NULL ORDER BY tag COLLATE NOCASE")
    abstract suspend fun getByVessel(vesselId: String): List<EquipmentEntity>

    /** Ids only — the due engine re-derives a whole vessel without loading every row. */
    @Query("SELECT id FROM equipment WHERE vesselId = :vesselId AND deletedAt IS NULL")
    abstract suspend fun idsForVessel(vesselId: String): List<String>

    @Upsert
    abstract suspend fun upsert(equipment: EquipmentEntity)

    @Upsert
    abstract suspend fun upsertAll(equipment: List<EquipmentEntity>)

    /**
     * The quick-action write of §7.3: one statement, so the marker recolours on the next frame.
     * [atMillis] is epoch-millis and updates `updatedAt` too, which import merge compares on.
     */
    @Query(
        """
        UPDATE equipment SET condition = :grade, conditionSetAt = :atMillis, updatedAt = :atMillis
        WHERE id = :id
        """,
    )
    abstract suspend fun setCondition(id: String, grade: ConditionGrade, atMillis: Long)

    @Query(
        """
        UPDATE equipment
        SET deckId = :deckId, zoneId = :zoneId, posX = :posX, posY = :posY, updatedAt = :atMillis
        WHERE id = :id
        """,
    )
    abstract suspend fun move(
        id: String,
        deckId: String?,
        zoneId: String?,
        posX: Float,
        posY: Float,
        atMillis: Long,
    )

    /** Soft delete — undoable for 10 seconds (C10) and propagated on import (§13.5). */
    @Query("UPDATE equipment SET deletedAt = :atMillis, updatedAt = :atMillis WHERE id = :id")
    abstract suspend fun softDelete(id: String, atMillis: Long)

    @Query("UPDATE equipment SET deletedAt = NULL, updatedAt = :atMillis WHERE id = :id")
    abstract suspend fun undelete(id: String, atMillis: Long)

    /** Hard delete. Only for purging tombstones; normal deletion is [softDelete]. */
    @Query("DELETE FROM equipment WHERE id = :id")
    abstract suspend fun deletePermanently(id: String)

    /**
     * Hard delete of everything on a vessel. Only for deleting the vessel itself — `equipment`
     * carries no foreign key (§13.5 needs rows to outlive their parents on import), so the
     * repository has to do what a cascade would.
     */
    @Query("DELETE FROM equipment WHERE vesselId = :vesselId")
    abstract suspend fun deleteByVessel(vesselId: String)

    /**
     * Return everything on a deck to the unplaced inbox — what deleting a deck does to the
     * equipment standing on it. Losing the items with the deck would breach C10.
     */
    @Query(
        """
        UPDATE equipment SET deckId = NULL, zoneId = NULL, updatedAt = :atMillis
        WHERE deckId = :deckId AND deletedAt IS NULL
        """,
    )
    abstract suspend fun unplaceAllOnDeck(deckId: String, atMillis: Long)

    /** Drop references to a zone that no longer exists; the equipment stays where it is. */
    @Query("UPDATE equipment SET zoneId = NULL, updatedAt = :atMillis WHERE zoneId = :zoneId")
    abstract suspend fun clearZoneReferences(zoneId: String, atMillis: Long)

    /** Denormalised due state written by the due engine — §11.1 step 5. Epoch-days. */
    @Query(
        """
        UPDATE equipment
        SET nextDueDate = :nextDueDate, nextDueTaskKey = :nextDueTaskKey, updatedAt = :atMillis
        WHERE id = :id
        """,
    )
    abstract suspend fun setNextDue(
        id: String,
        nextDueDate: Long?,
        nextDueTaskKey: String?,
        atMillis: Long,
    )

    /**
     * Highest numeric suffix already used by a tag beginning with [prefix] on this vessel, or null
     * when there is none — the auto-numbering of §7.5 step 3 (`FE-UD-` -> `FE-UD-03`).
     *
     * The prefix is compared with `SUBSTR`, not `LIKE`, so a prefix containing `%` or `_` cannot
     * match more than it should. The `NOT GLOB '*[^0-9]*'` guard rejects any tail that is not all
     * digits, so `FE-UD-SPARE` never contributes `0` to the maximum. Soft-deleted rows are
     * excluded: a deleted `FE-UD-07` frees its number for reuse.
     */
    @Query(
        """
        SELECT MAX(CAST(SUBSTR(tag, LENGTH(:prefix) + 1) AS INTEGER)) FROM equipment
        WHERE vesselId = :vesselId
          AND deletedAt IS NULL
          AND LENGTH(tag) > LENGTH(:prefix)
          AND SUBSTR(tag, 1, LENGTH(:prefix)) = :prefix
          AND SUBSTR(tag, LENGTH(:prefix) + 1) NOT GLOB '*[^0-9]*'
        """,
    )
    abstract suspend fun maxTagSuffix(vesselId: String, prefix: String): Int?

    /** The next free number for [prefix]; 1 when nothing with that prefix exists yet. */
    suspend fun nextTagNumber(vesselId: String, prefix: String): Int =
        (maxTagSuffix(vesselId, prefix) ?: 0) + 1

    // ---- Logical categories (equipment_category_xref) -----------------------------------------

    @Query("SELECT categoryId FROM equipment_category_xref WHERE equipmentId = :equipmentId ORDER BY categoryId")
    abstract fun observeCategoryIds(equipmentId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCategoryXrefs(rows: List<EquipmentCategoryXref>)

    @Query("DELETE FROM equipment_category_xref WHERE equipmentId = :equipmentId")
    abstract suspend fun clearCategories(equipmentId: String)

    /** Replace the whole set in one transaction so no observer sees a half-applied selection. */
    @Transaction
    open suspend fun setCategories(equipmentId: String, categoryIds: List<String>) {
        clearCategories(equipmentId)
        insertCategoryXrefs(equipmentCategoryXrefs(equipmentId, categoryIds))
    }
}
