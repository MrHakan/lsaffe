package com.deckwatch.data.repository

import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.database.dao.DeficiencyDao
import com.deckwatch.core.database.dao.RoundDao
import com.deckwatch.core.database.dao.RoundItemDao
import com.deckwatch.core.database.mappers.toEntity
import com.deckwatch.core.database.mappers.toModel
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [InspectionRepository] — MASTER_PROMPT §6.7 and §6.8.
 *
 * Ordering is the DAO's: rounds newest first, deficiencies by severity then raise date, so the
 * critical detainable item is at the top of the list an officer opens before a PSC inspection.
 */
@Singleton
class RoomInspectionRepository @Inject constructor(
    private val roundDao: RoundDao,
    private val roundItemDao: RoundItemDao,
    private val deficiencyDao: DeficiencyDao,
) : InspectionRepository {

    override fun observeRounds(vesselId: String): Flow<List<Round>> =
        roundDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getRound(id: String): Round? = roundDao.getById(id)?.toModel()

    override suspend fun upsertRound(round: Round) = roundDao.upsert(round.toEntity())

    override fun observeRoundItems(roundId: String): Flow<List<RoundItem>> =
        roundItemDao.observeByRound(roundId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertRoundItem(item: RoundItem) = roundItemDao.upsert(item.toEntity())

    override fun observeDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        deficiencyDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    /** `OPEN` and `IN_PROGRESS` only — `DEFERRED_TO_OFFICE` is off the ship's list (§6.8). */
    override fun observeOpenDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        deficiencyDao.observeOpenByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getDeficiency(id: String): Deficiency? =
        deficiencyDao.getById(id)?.toModel()

    override suspend fun upsertDeficiency(deficiency: Deficiency) =
        deficiencyDao.upsert(deficiency.toEntity())
}
