package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.deckwatch.core.database.entity.CategoryEntity
import com.deckwatch.core.database.entity.ZoneEntity
import kotlinx.coroutines.flow.Flow

/** Spatial zones drawn on a deck plan — MASTER_PROMPT §6.4. */
@Dao
interface ZoneDao {

    @Query("SELECT * FROM zones WHERE deckId = :deckId ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeByDeck(deckId: String): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getById(id: String): ZoneEntity?

    @Query("SELECT * FROM zones WHERE deckId = :deckId ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getByDeck(deckId: String): List<ZoneEntity>

    @Upsert
    suspend fun upsert(zone: ZoneEntity)

    @Upsert
    suspend fun upsertAll(zones: List<ZoneEntity>)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM zones WHERE deckId = :deckId")
    suspend fun deleteByDeck(deckId: String)
}

/** Logical categories — MASTER_PROMPT §6.4. A row with a null `vesselId` is global. */
@Dao
interface CategoryDao {

    /**
     * Global categories (`vesselId IS NULL`) are always returned alongside the vessel's own, so a
     * "PSC Focus Items" tag defined once follows the officer onto the next ship.
     */
    @Query(
        """
        SELECT * FROM categories
        WHERE vesselId IS NULL OR vesselId = :vesselId
        ORDER BY sortOrder, name COLLATE NOCASE
        """,
    )
    fun observeForVessel(vesselId: String?): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE vesselId IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeGlobal(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
