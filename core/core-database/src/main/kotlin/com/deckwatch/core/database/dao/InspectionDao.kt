package com.deckwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.deckwatch.core.database.entity.DeficiencyEntity
import com.deckwatch.core.database.entity.RoundEntity
import com.deckwatch.core.database.entity.RoundItemEntity
import kotlinx.coroutines.flow.Flow

/** Inspection rounds — MASTER_PROMPT §6.7. */
@Dao
interface RoundDao {

    @Query("SELECT * FROM rounds WHERE vesselId = :vesselId ORDER BY startedAt DESC")
    fun observeByVessel(vesselId: String): Flow<List<RoundEntity>>

    /** Sweep mode resumes the round it is still filling in — §7.3. */
    @Query("SELECT * FROM rounds WHERE vesselId = :vesselId AND completedAt IS NULL ORDER BY startedAt DESC")
    fun observeOpenByVessel(vesselId: String): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE id = :id")
    suspend fun getById(id: String): RoundEntity?

    @Upsert
    suspend fun upsert(round: RoundEntity)

    @Upsert
    suspend fun upsertAll(rounds: List<RoundEntity>)

    @Query("DELETE FROM rounds WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** Items within a round — MASTER_PROMPT §6.7. */
@Dao
interface RoundItemDao {

    @Query("SELECT * FROM round_items WHERE roundId = :roundId ORDER BY checkedAt IS NULL DESC, checkedAt ASC")
    fun observeByRound(roundId: String): Flow<List<RoundItemEntity>>

    /** Sweep mode advances to the next unchecked item on the deck — §7.3. */
    @Query("SELECT * FROM round_items WHERE roundId = :roundId AND checkedAt IS NULL ORDER BY id LIMIT 1")
    suspend fun nextUnchecked(roundId: String): RoundItemEntity?

    @Query("SELECT * FROM round_items WHERE id = :id")
    suspend fun getById(id: String): RoundItemEntity?

    @Query("SELECT * FROM round_items WHERE equipmentId = :equipmentId ORDER BY checkedAt DESC")
    fun observeByEquipment(equipmentId: String): Flow<List<RoundItemEntity>>

    @Upsert
    suspend fun upsert(item: RoundItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<RoundItemEntity>)

    @Query("DELETE FROM round_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM round_items WHERE roundId = :roundId")
    suspend fun deleteByRound(roundId: String)
}

/**
 * Deficiencies — MASTER_PROMPT §6.8.
 *
 * "Open" is `OPEN` or `IN_PROGRESS`. `DEFERRED_TO_OFFICE` is deliberately **not** open: it is off
 * the ship's list but still visible in the full list and in the deficiency report (§13.3).
 */
@Dao
interface DeficiencyDao {

    @Query("SELECT * FROM deficiencies WHERE vesselId = :vesselId ORDER BY raisedDate DESC")
    fun observeByVessel(vesselId: String): Flow<List<DeficiencyEntity>>

    /**
     * Severity is stored by `name`, so it cannot be ordered lexically — that would put
     * `OBSERVATION` above `CRITICAL_DETAINABLE`. The `CASE` spells out the real ranking.
     */
    @Query(
        """
        SELECT * FROM deficiencies
        WHERE vesselId = :vesselId AND status IN ('OPEN', 'IN_PROGRESS')
        ORDER BY CASE severity
                     WHEN 'CRITICAL_DETAINABLE' THEN 0
                     WHEN 'MAJOR' THEN 1
                     WHEN 'MINOR' THEN 2
                     ELSE 3
                 END,
                 raisedDate DESC
        """,
    )
    fun observeOpenByVessel(vesselId: String): Flow<List<DeficiencyEntity>>

    @Query(
        """
        SELECT * FROM deficiencies
        WHERE equipmentId = :equipmentId AND status IN ('OPEN', 'IN_PROGRESS')
        ORDER BY raisedDate DESC
        """,
    )
    fun observeOpenByEquipment(equipmentId: String): Flow<List<DeficiencyEntity>>

    @Query("SELECT * FROM deficiencies WHERE id = :id")
    suspend fun getById(id: String): DeficiencyEntity?

    @Query("SELECT COUNT(*) FROM deficiencies WHERE vesselId = :vesselId AND status IN ('OPEN', 'IN_PROGRESS')")
    fun observeOpenCount(vesselId: String): Flow<Int>

    @Upsert
    suspend fun upsert(deficiency: DeficiencyEntity)

    @Upsert
    suspend fun upsertAll(deficiencies: List<DeficiencyEntity>)

    @Query("DELETE FROM deficiencies WHERE id = :id")
    suspend fun deleteById(id: String)
}
