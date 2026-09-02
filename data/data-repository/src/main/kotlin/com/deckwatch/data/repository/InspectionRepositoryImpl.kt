package com.deckwatch.data.repository

import com.deckwatch.core.common.DispatcherProvider
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
import kotlinx.coroutines.withContext

/**
 * Inspection rounds, their items and deficiencies — MASTER_PROMPT §6.7, §6.8.
 *
 * Straight delegation: a round is written item by item as the officer sweeps a deck (§7.3), so
 * there is no multi-row invariant to hold here — each write is already atomic on its own row.
 * "Open" for deficiencies means `OPEN` or `IN_PROGRESS`; `DEFERRED_TO_OFFICE` is off the ship's
 * list but still in the full list and in the deficiency report (§13.3), and the DAO encodes that.
 */
@Singleton
class InspectionRepositoryImpl @Inject constructor(
    private val roundDao: RoundDao,
    private val roundItemDao: RoundItemDao,
    private val deficiencyDao: DeficiencyDao,
    private val dispatchers: DispatcherProvider,
) : InspectionRepository {

    override fun observeRounds(vesselId: String): Flow<List<Round>> =
        roundDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getRound(id: String): Round? = withContext(dispatchers.io) {
        roundDao.getById(id)?.toModel()
    }

    override suspend fun upsertRound(round: Round) = withContext(dispatchers.io) {
        roundDao.upsert(round.toEntity())
    }

    override fun observeRoundItems(roundId: String): Flow<List<RoundItem>> =
        roundItemDao.observeByRound(roundId).map { rows -> rows.map { it.toModel() } }

    override suspend fun upsertRoundItem(item: RoundItem) = withContext(dispatchers.io) {
        roundItemDao.upsert(item.toEntity())
    }

    override fun observeDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        deficiencyDao.observeByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override fun observeOpenDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        deficiencyDao.observeOpenByVessel(vesselId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getDeficiency(id: String): Deficiency? = withContext(dispatchers.io) {
        deficiencyDao.getById(id)?.toModel()
    }

    override suspend fun upsertDeficiency(deficiency: Deficiency) = withContext(dispatchers.io) {
        deficiencyDao.upsert(deficiency.toEntity())
    }
}
