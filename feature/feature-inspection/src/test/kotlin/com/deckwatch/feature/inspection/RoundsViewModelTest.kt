package com.deckwatch.feature.inspection

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.testing.FakeEquipmentRepository
import com.deckwatch.core.testing.FakeInspectionRepository
import com.deckwatch.core.testing.FakeReferenceRepository
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.SequentialIds
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Round history and the start-a-round flow — §6.7, and the list-mode sweep of §7.1 C. */
class RoundsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vessels = FakeVesselRepository()
    private val equipment = FakeEquipmentRepository()
    private val inspection = FakeInspectionRepository()
    private val reference = FakeReferenceRepository()

    private fun viewModel(ids: () -> String = SequentialIds("round")) = RoundsViewModel(
        vesselRepository = vessels,
        equipmentRepository = equipment,
        inspectionRepository = inspection,
        referenceRepository = reference,
        clock = { TestData.referenceMillis },
        idFactory = ids,
    )

    @Before
    fun seed() {
        val vessel = TestData.vessel(id = VESSEL, isActive = true)
        vessels.vessels.value = mapOf(vessel.id to vessel)
        val deck = TestData.deck(id = "deck-upper", vesselId = VESSEL, shortCode = "UD", levelIndex = 0)
        vessels.decks.value = mapOf(deck.id to deck)

        reference.seedEquipmentType(TestData.equipmentType(typeKey = FE_TYPE, group = EquipmentGroup.FFE))
        reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = LB_TYPE,
                group = EquipmentGroup.LSA,
                nameEn = "Lifebuoy",
                nameTr = "Can simidi",
                symbolKey = "LSS005",
                defaultTagPrefix = "LB",
                attributeSchema = emptyList(),
                taskKeys = emptyList(),
            ),
        )
        reference.seedRoundTemplate(
            TestData.roundTemplate(
                key = "WEEKLY_LSA",
                titleEn = "Weekly LSA round",
                titleTr = "Haftalık LSA turu",
                includesGroups = listOf(EquipmentGroup.LSA),
            ),
        )
        reference.seedRoundTemplate(
            TestData.roundTemplate(
                key = "MONTHLY_ENGINE",
                titleEn = "Monthly engine-room round",
                titleTr = "Aylık makine dairesi turu",
                includesGroups = listOf(EquipmentGroup.MACHINERY_CONTROLS),
            ),
        )

        val extinguisher = TestData.equipment(
            id = "e-fe",
            vesselId = VESSEL,
            deckId = "deck-upper",
            typeKey = FE_TYPE,
            tag = "FE-UD-01",
        )
        val buoyOne = TestData.equipment(
            id = "e-lb1",
            vesselId = VESSEL,
            deckId = "deck-upper",
            typeKey = LB_TYPE,
            symbolKey = "LSS005",
            tag = "LB-01",
            condition = ConditionGrade.NOT_CHECKED,
        )
        val buoyTwo = buoyOne.copy(id = "e-lb2", tag = "LB-02")
        equipment.equipment.value = listOf(extinguisher, buoyOne, buoyTwo).associateBy { it.id }
    }

    @Test
    fun `templates are offered with the number of items they would cover`() = runTest {
        val state = viewModel().uiState.first { !it.loading }
        assertThat(state.hasVessel).isTrue()
        val weekly = state.templates.single { it.key == "WEEKLY_LSA" }
        assertThat(weekly.title.resolve(turkish = false)).isEqualTo("Weekly LSA round")
        assertThat(weekly.title.resolve(turkish = true)).isEqualTo("Haftalık LSA turu")
        assertThat(weekly.matchCount).isEqualTo(2)
        assertThat(state.templates.single { it.key == "MONTHLY_ENGINE" }.matchCount).isEqualTo(0)
    }

    @Test
    fun `starting a round writes the round, its items and reports the new id`() = runTest {
        val model = viewModel()
        // One collection primes the catalogue the start-round tap reads from.
        model.uiState.first { !it.loading }
        model.startRound("WEEKLY_LSA", "3/O")

        val round = inspection.rounds.value.values.single()
        assertThat(round.id).isEqualTo("round-1")
        assertThat(round.templateKey).isEqualTo("WEEKLY_LSA")
        assertThat(round.title).isEqualTo("Weekly LSA round")
        assertThat(round.performedBy).isEqualTo("3/O")
        assertThat(round.itemCount).isEqualTo(2)
        assertThat(round.startedAt).isEqualTo(TestData.referenceMillis)
        assertThat(round.completedAt).isNull()

        val items = inspection.roundItems.value.values
        assertThat(items).hasSize(2)
        assertThat(items.map { it.equipmentId }).containsExactly("e-lb1", "e-lb2")
        assertThat(items.map { it.condition }.distinct()).containsExactly(null)

        assertThat(model.event.value).isEqualTo(RoundsEvent.Started("round-1"))
        model.consumeEvent()
        assertThat(model.event.value).isNull()
    }

    @Test
    fun `a template matching nothing is reported rather than written`() = runTest {
        val model = viewModel()
        model.uiState.first { !it.loading }
        model.startRound("MONTHLY_ENGINE", "3/O")

        assertThat(inspection.rounds.value).isEmpty()
        assertThat(inspection.roundItems.value).isEmpty()
        assertThat(model.event.value).isEqualTo(RoundsEvent.NoMatchingEquipment)
    }

    @Test
    fun `history lists the vessel's rounds newest first`() = runTest {
        val older = TestData.round(id = "r1", vesselId = VESSEL, startedAt = 1_000L, title = "Older")
        val newer = TestData.round(id = "r2", vesselId = VESSEL, startedAt = 2_000L, title = "Newer")
        inspection.rounds.value = mapOf(older.id to older, newer.id to newer)

        val state = viewModel().uiState.first { !it.loading }
        assertThat(state.rounds.map { it.id }).containsExactly("r2", "r1").inOrder()
    }

    private companion object {
        const val VESSEL = "vessel-under-test"
        const val FE_TYPE = "FFE_PORTABLE_EXTINGUISHER"
        const val LB_TYPE = "LSA_LIFEBUOY"
    }
}
