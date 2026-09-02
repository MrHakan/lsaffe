package com.deckwatch.feature.report

import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.testing.FakeInspectionRepository
import kotlinx.coroutines.flow.Flow

/**
 * An [InspectionRepository] that fails on the first round write.
 *
 * Rounds sit late in the write order (§13.5), which makes this the way to reach phase 2 with a
 * dozen successful writes already behind it — exactly the state [ImportApplier]'s rollback exists
 * for. Reads delegate to the real fake so the snapshot is honest.
 */
class ExplodingInspectionRepository(
    private val delegate: FakeInspectionRepository,
) : InspectionRepository by delegate {

    override suspend fun upsertRound(round: Round) {
        error("no room in the log book")
    }

    override fun observeRounds(vesselId: String): Flow<List<Round>> = delegate.observeRounds(vesselId)

    override suspend fun getRound(id: String): Round? = delegate.getRound(id)

    override fun observeRoundItems(roundId: String): Flow<List<RoundItem>> =
        delegate.observeRoundItems(roundId)

    override fun observeDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        delegate.observeDeficiencies(vesselId)
}
