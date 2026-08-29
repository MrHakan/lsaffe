package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.deckwatch.core.database.entity.DeckEntity
import kotlinx.coroutines.flow.Flow

/**
 * Serves `VesselRepository`'s deck half — MASTER_PROMPT §6.2.
 *
 * This DAO deliberately does **not** use `@Upsert`. `decks` carries a unique index on
 * `(vesselId, levelIndex)`, and Room's upsert treats *any* uniqueness failure as "the row already
 * exists" and falls back to an update by primary key — which, for a genuinely new deck that clashes
 * on `levelIndex`, matches nothing and silently discards the insert. C10 forbids losing data
 * silently, so [upsert] resolves insert-versus-update by primary key itself and lets a real level
 * clash surface as a constraint violation.
 */
@Dao
abstract class DeckDao {

    /**
     * Stack order: highest deck first, because the 2.5D stack renders `levelIndex` descending
     * with the highest deck at the top of the screen (§7.1 A).
     */
    @Query("SELECT * FROM decks WHERE vesselId = :vesselId ORDER BY levelIndex DESC")
    abstract fun observeByVessel(vesselId: String): Flow<List<DeckEntity>>

    /** Keel-up order, for list mode and for exports that read bottom deck first. */
    @Query("SELECT * FROM decks WHERE vesselId = :vesselId ORDER BY levelIndex ASC")
    abstract fun observeByVesselBottomUp(vesselId: String): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    abstract suspend fun getById(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE vesselId = :vesselId ORDER BY levelIndex DESC")
    abstract suspend fun getByVessel(vesselId: String): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE vesselId = :vesselId AND levelIndex = :levelIndex")
    abstract suspend fun getByLevelIndex(vesselId: String, levelIndex: Int): DeckEntity?

    /** null when the vessel has no decks yet — the first deck then takes `levelIndex = 0` (§6.2). */
    @Query("SELECT MAX(levelIndex) FROM decks WHERE vesselId = :vesselId")
    abstract suspend fun maxLevelIndex(vesselId: String): Int?

    @Query("SELECT MIN(levelIndex) FROM decks WHERE vesselId = :vesselId")
    abstract suspend fun minLevelIndex(vesselId: String): Int?

    @Query("SELECT COUNT(*) FROM decks WHERE vesselId = :vesselId")
    abstract suspend fun countForVessel(vesselId: String): Int

    /** Fails loudly on a duplicate `(vesselId, levelIndex)` or an unknown vessel. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(deck: DeckEntity)

    @Update
    abstract suspend fun update(deck: DeckEntity)

    @Transaction
    open suspend fun upsert(deck: DeckEntity) {
        if (getById(deck.id) == null) insert(deck) else update(deck)
    }

    @Transaction
    open suspend fun upsertAll(decks: List<DeckEntity>) {
        decks.forEach { upsert(it) }
    }

    @Query("DELETE FROM decks WHERE id = :id")
    abstract suspend fun deleteById(id: String)
}
