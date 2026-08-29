package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.deckwatch.core.database.entity.VesselEntity
import kotlinx.coroutines.flow.Flow

/** Serves `VesselRepository`'s vessel half — MASTER_PROMPT §6.1. */
@Dao
abstract class VesselDao {

    @Query("SELECT * FROM vessels ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<VesselEntity>>

    /** The app supports several vessels but exactly one is active at a time — §5. */
    @Query("SELECT * FROM vessels WHERE isActive = 1 LIMIT 1")
    abstract fun observeActive(): Flow<VesselEntity?>

    @Query("SELECT * FROM vessels WHERE id = :id")
    abstract suspend fun getById(id: String): VesselEntity?

    @Query("SELECT * FROM vessels WHERE isActive = 1 LIMIT 1")
    abstract suspend fun getActive(): VesselEntity?

    @Upsert
    abstract suspend fun upsert(vessel: VesselEntity)

    @Upsert
    abstract suspend fun upsertAll(vessels: List<VesselEntity>)

    /** Cascades to `decks` by foreign key. */
    @Query("DELETE FROM vessels WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("UPDATE vessels SET isActive = 0 WHERE isActive = 1")
    abstract suspend fun clearActive()

    @Query("UPDATE vessels SET isActive = 1 WHERE id = :id")
    abstract suspend fun markActive(id: String)

    /**
     * Switch the active vessel in one transaction so no observer can ever see two active vessels
     * (or none) mid-switch — the vessel selector in the top app bar reads this continuously.
     */
    @Transaction
    open suspend fun setActive(id: String) {
        clearActive()
        markActive(id)
    }
}
