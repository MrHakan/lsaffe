package com.deckwatch.data.repository

import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RoomInspectionRepositoryTest : RepositoryTest() {

    private val repository get() = inspectionRepository()

    @Before
    fun seedVessel() = runTest {
        vesselRepository().upsertVessel(TestData.vessel(id = VESSEL))
    }

    @Test
    fun `a round and its items round-trip`() = runTest {
        val repository = repository
        repository.upsertRound(TestData.round(id = ROUND, vesselId = VESSEL))
        repository.upsertRoundItem(
            TestData.roundItem(id = "item-1", roundId = ROUND, equipmentId = "equipment-1"),
        )

        assertThat(repository.getRound(ROUND)?.title).isEqualTo("Weekly LSA round")
        assertThat(repository.observeRounds(VESSEL).first()).hasSize(1)
        assertThat(repository.observeRoundItems(ROUND).first().map { it.id })
            .containsExactly("item-1")
    }

    @Test
    fun `open deficiencies exclude the ones deferred to the office and lead with the worst`() =
        runTest {
            val repository = repository
            repository.upsertDeficiency(
                deficiency("def-minor", Severity.MINOR, DeficiencyStatus.OPEN),
            )
            repository.upsertDeficiency(
                deficiency("def-critical", Severity.CRITICAL_DETAINABLE, DeficiencyStatus.IN_PROGRESS),
            )
            repository.upsertDeficiency(
                deficiency("def-office", Severity.MAJOR, DeficiencyStatus.DEFERRED_TO_OFFICE),
            )

            assertThat(repository.observeOpenDeficiencies(VESSEL).first().map { it.id })
                .containsExactly("def-critical", "def-minor")
                .inOrder()
            // The deferred one is off the ship's list but stays in the full list — §6.8.
            assertThat(repository.observeDeficiencies(VESSEL).first()).hasSize(3)
            assertThat(repository.getDeficiency("def-office")?.status)
                .isEqualTo(DeficiencyStatus.DEFERRED_TO_OFFICE)
        }

    private fun deficiency(id: String, severity: Severity, status: DeficiencyStatus) =
        TestData.deficiency(
            id = id,
            vesselId = VESSEL,
            equipmentId = null,
            severity = severity,
            status = status,
        )

    private companion object {
        const val VESSEL = "vessel-a"
        const val ROUND = "round-a"
    }
}
