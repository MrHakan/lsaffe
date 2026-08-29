package com.deckwatch.feature.inspection

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.FakeEquipmentRepository
import com.deckwatch.core.testing.FakeInspectionRepository
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Walking a round item by item — the list-mode sweep of §7.1 C and the grading of §7.3. */
class RoundRunViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vessels = FakeVesselRepository()
    private val equipment = FakeEquipmentRepository()
    private val inspection = FakeInspectionRepository()

    private val now = TestData.referenceMillis

    private fun viewModel() = RoundRunViewModel(
        vesselRepository = vessels,
        equipmentRepository = equipment,
        inspectionRepository = inspection,
        clock = { now },
    )

    @Before
    fun seed() {
        val vessel = TestData.vessel(id = VESSEL, isActive = true)
        vessels.vessels.value = mapOf(vessel.id to vessel)
        val deck = TestData.deck(id = "deck-upper", vesselId = VESSEL, shortCode = "UD", levelIndex = 0)
        vessels.decks.value = mapOf(deck.id to deck)

        val first = TestData.equipment(
            id = "e-1",
            vesselId = VESSEL,
            deckId = "deck-upper",
            tag = "FE-UD-01",
            condition = ConditionGrade.NOT_CHECKED,
        )
        val second = first.copy(id = "e-2", tag = "FE-UD-02")
        equipment.equipment.value = mapOf(first.id to first, second.id to second)

        val round = TestData.round(
            id = ROUND,
            vesselId = VESSEL,
            title = "Weekly LSA round",
            itemCount = 2,
        )
        inspection.rounds.value = mapOf(round.id to round)
        inspection.roundItems.value = listOf(
            TestData.roundItem(id = "item-1", roundId = ROUND, equipmentId = "e-1"),
            TestData.roundItem(id = "item-2", roundId = ROUND, equipmentId = "e-2"),
        ).associateBy { it.id }
    }

    @Test
    fun `the run screen joins items to their equipment`() = runTest {
        val model = viewModel()
        model.bind(ROUND)
        val state = model.uiState.first { !it.loading }

        assertThat(state.title).isEqualTo("Weekly LSA round")
        assertThat(state.itemCount).isEqualTo(2)
        assertThat(state.doneCount).isEqualTo(0)
        assertThat(state.items.map { it.tag }).containsExactly("FE-UD-01", "FE-UD-02").inOrder()
        assertThat(state.items.first().deckShortName).isEqualTo("UD")
        assertThat(state.items.first().checked).isFalse()
    }

    @Test
    fun `grading writes the round item, the equipment condition and the round counts`() = runTest {
        val model = viewModel()
        model.bind(ROUND)
        model.uiState.first { !it.loading }

        model.grade("item-1", ConditionGrade.DEFECTIVE)

        val item = inspection.roundItems.value.getValue("item-1")
        assertThat(item.condition).isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(item.checkedAt).isEqualTo(now)

        assertThat(equipment.equipment.value.getValue("e-1").condition)
            .isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(equipment.equipment.value.getValue("e-1").conditionSetAt).isEqualTo(now)

        val round = inspection.rounds.value.getValue(ROUND)
        assertThat(round.doneCount).isEqualTo(1)
        assertThat(round.deficiencyCount).isEqualTo(1)
        assertThat(round.completedAt).isNull()
    }

    @Test
    fun `a good grade does not count as a deficiency`() = runTest {
        val model = viewModel()
        model.bind(ROUND)
        model.uiState.first { !it.loading }

        model.grade("item-1", ConditionGrade.GOOD)

        val round = inspection.rounds.value.getValue(ROUND)
        assertThat(round.doneCount).isEqualTo(1)
        assertThat(round.deficiencyCount).isEqualTo(0)
    }

    @Test
    fun `a remark is stored without marking the item checked`() = runTest {
        val model = viewModel()
        model.bind(ROUND)
        model.uiState.first { !it.loading }

        model.setRemark("item-2", "Bracket loose, tightened")

        val item = inspection.roundItems.value.getValue("item-2")
        assertThat(item.remark).isEqualTo("Bracket loose, tightened")
        assertThat(item.condition).isNull()
        assertThat(inspection.rounds.value.getValue(ROUND).doneCount).isEqualTo(0)
    }

    @Test
    fun `finishing stamps completedAt and keeps skipped items out of the done count`() = runTest {
        val model = viewModel()
        model.bind(ROUND)
        model.uiState.first { !it.loading }

        model.grade("item-1", ConditionGrade.ACCEPTABLE)
        model.finish()

        val round = inspection.rounds.value.getValue(ROUND)
        assertThat(round.completedAt).isEqualTo(now)
        assertThat(round.itemCount).isEqualTo(2)
        assertThat(round.doneCount).isEqualTo(1)
        assertThat(round.deficiencyCount).isEqualTo(0)
    }

    private companion object {
        const val VESSEL = "vessel-under-test"
        const val ROUND = "round-under-test"
    }
}
