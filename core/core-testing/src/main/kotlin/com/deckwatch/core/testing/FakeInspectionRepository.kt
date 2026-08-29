package com.deckwatch.core.testing

import com.deckwatch.core.common.repository.InspectionRepository
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [InspectionRepository]: rounds, round items and deficiencies — §6.7, §6.8. */
class FakeInspectionRepository : InspectionRepository {

    val rounds = MutableStateFlow<Map<String, Round>>(emptyMap())
    val roundItems = MutableStateFlow<Map<String, RoundItem>>(emptyMap())
    val deficiencies = MutableStateFlow<Map<String, Deficiency>>(emptyMap())

    /** Most recent round first — the history list reads newest-down. */
    override fun observeRounds(vesselId: String): Flow<List<Round>> =
        rounds.map { current ->
            current.values.filter { it.vesselId == vesselId }.sortedByDescending { it.startedAt }
        }

    override suspend fun getRound(id: String): Round? = rounds.value[id]

    override suspend fun upsertRound(round: Round) {
        rounds.update { it + (round.id to round) }
    }

    override fun observeRoundItems(roundId: String): Flow<List<RoundItem>> =
        roundItems.map { current ->
            current.values.filter { it.roundId == roundId }.sortedBy { it.id }
        }

    override suspend fun upsertRoundItem(item: RoundItem) {
        roundItems.update { it + (item.id to item) }
    }

    /** Worst severity first, then oldest — the order the Due and deficiency lists want. */
    override fun observeDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        deficiencies.map { current ->
            current.values
                .filter { it.vesselId == vesselId }
                .sortedWith(compareByDescending<Deficiency> { it.severity.ordinal }.thenBy { it.raisedDate })
        }

    /** `CLOSED` deficiencies drop out; `DEFERRED_TO_OFFICE` is still open work — §6.8. */
    override fun observeOpenDeficiencies(vesselId: String): Flow<List<Deficiency>> =
        observeDeficiencies(vesselId).map { list ->
            list.filter { it.status != DeficiencyStatus.CLOSED }
        }

    override suspend fun getDeficiency(id: String): Deficiency? = deficiencies.value[id]

    override suspend fun upsertDeficiency(deficiency: Deficiency) {
        deficiencies.update { it + (deficiency.id to deficiency) }
    }
}
