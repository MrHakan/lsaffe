package com.deckwatch.feature.vessel

import app.cash.turbine.test
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
import com.deckwatch.feature.vessel.deck.DeckDraft
import com.deckwatch.feature.vessel.deck.DeckManagerViewModel
import com.deckwatch.feature.vessel.deck.DeckManagerUiState
import com.deckwatch.feature.vessel.deck.InsertSlot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeckManagerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val log = CallLog()
    private val fakes = FakeRepositories()
    private val vessels = RecordingVesselRepository(fakes.vessels, log)
    private val equipment = RecordingEquipmentRepository(fakes.equipment, log)
    private val vessel = TestData.vessel(id = "vessel-1", name = "MV Example", isActive = true)

    private fun viewModel() = DeckManagerViewModel(vessels, equipment, fakes.reference)

    @Before
    fun seedVessel() = runTest {
        fakes.vessels.upsertVessel(vessel)
    }

    // ------------------------------------------------------------------ ordering

    @Test
    fun `decks come out highest level first with their equipment counts`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-bridge", vesselId = "vessel-1", levelIndex = 20))
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-engine", vesselId = "vessel-1", levelIndex = -10))
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", vesselId = "vessel-1", deckId = "deck-upper", tag = "FE-01"),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e2", vesselId = "vessel-1", deckId = "deck-upper", tag = "FE-02"),
        )

        val viewModel = viewModel()
        viewModel.bind(null)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertThat(state.decks.map { it.id })
                .containsExactly("deck-bridge", "deck-upper", "deck-engine")
                .inOrder()
            assertThat(state.decks.first { it.id == "deck-upper" }.equipmentCount).isEqualTo(2)
            assertThat(state.decks.first { it.id == "deck-bridge" }.equipmentCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each deck reports the worst condition standing on it`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", deckId = "deck-upper", tag = "A", condition = ConditionGrade.GOOD),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e2", deckId = "deck-upper", tag = "B", condition = ConditionGrade.DEFECTIVE),
        )

        val viewModel = viewModel()
        viewModel.bind(null)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertThat(state.decks.single().worstCondition).isEqualTo(ConditionGrade.DEFECTIVE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insert-between targets are computed from the repository's level indices`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "d0", vesselId = "vessel-1", levelIndex = 0))
        fakes.vessels.upsertDeck(TestData.deck(id = "d10", vesselId = "vessel-1", levelIndex = 10))
        fakes.vessels.upsertDeck(TestData.deck(id = "d11", vesselId = "vessel-1", levelIndex = 11))

        val viewModel = viewModel()
        viewModel.bind(null)

        viewModel.uiState.test {
            val slots = awaitLoaded().insertSlots
            assertThat(slots).containsExactly(
                InsertSlot(lowerLevelIndex = 10, upperLevelIndex = 11, enabled = false),
                InsertSlot(lowerLevelIndex = 0, upperLevelIndex = 10, enabled = true),
            ).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a null vessel id resolves to the active vessel`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-2", name = "MT Other", isActive = false))
        val viewModel = viewModel()
        viewModel.bind(null)

        viewModel.uiState.test {
            assertThat(awaitLoaded().vessel?.id).isEqualTo("vessel-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an explicit vessel id wins over the active vessel`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-2", name = "MT Other", isActive = false))
        val viewModel = viewModel()
        viewModel.bind("vessel-2")

        viewModel.uiState.test {
            assertThat(awaitLoaded().vessel?.id).isEqualTo("vessel-2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------ creation

    @Test
    fun `the repository owns the level index for add above and add below`() = runTest {
        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitLoaded()

            viewModel.openAddAbove()
            viewModel.saveDraft(draft("Upper Deck"))
            advanceUntilIdle()

            viewModel.openAddAbove()
            viewModel.saveDraft(draft("Bridge Deck"))
            advanceUntilIdle()

            viewModel.openAddBelow()
            viewModel.saveDraft(draft("Engine Room Flat"))
            advanceUntilIdle()

            cancelAndIgnoreRemainingEvents()
        }

        val byName = fakes.vessels.decks.value.values.associateBy { it.name }
        assertThat(byName.getValue("Upper Deck").levelIndex).isEqualTo(0)
        assertThat(byName.getValue("Bridge Deck").levelIndex).isEqualTo(10)
        assertThat(byName.getValue("Engine Room Flat").levelIndex).isEqualTo(-10)
    }

    @Test
    fun `insert between goes through the repository and lands on the midpoint`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "d0", vesselId = "vessel-1", levelIndex = 0))
        fakes.vessels.upsertDeck(TestData.deck(id = "d10", vesselId = "vessel-1", levelIndex = 10))

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.openInsertBetween(InsertSlot(lowerLevelIndex = 0, upperLevelIndex = 10, enabled = true))
            viewModel.saveDraft(draft("A Deck"))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(log.all()).contains("insertDeckBetween:0:10")
        val inserted = fakes.vessels.decks.value.values.single { it.name == "A Deck" }
        assertThat(inserted.levelIndex).isEqualTo(5)
    }

    @Test
    fun `a disabled slot opens nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.openInsertBetween(InsertSlot(lowerLevelIndex = 0, upperLevelIndex = 1, enabled = false))

        assertThat(viewModel.sheet.value).isNull()
    }

    @Test
    fun `tint and notes are written back after the repository has created the deck`() = runTest {
        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.openAddAbove()
            viewModel.saveDraft(
                DeckDraft(
                    name = "Upper Deck",
                    shortCode = "UD",
                    plan = BuiltInPlanPresets.all.first().plan,
                    colorTint = 0xFF1B873F.toInt(),
                    notes = "Weather deck",
                ),
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val deck = fakes.vessels.decks.value.values.single()
        assertThat(deck.shortCode).isEqualTo("UD")
        assertThat(deck.colorTint).isEqualTo(0xFF1B873F.toInt())
        assertThat(deck.notes).isEqualTo("Weather deck")
    }

    // ------------------------------------------------------------------ delete

    @Test
    fun `deleting a deck unplaces its equipment before removing the deck`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", vesselId = "vessel-1", deckId = "deck-upper", tag = "FE-01"),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e2", vesselId = "vessel-1", deckId = "deck-upper", tag = "FE-02"),
        )

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.confirmDeleteDeck("deck-upper")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val calls = log.all()
        assertThat(calls).containsAtLeast("move:e1:unplaced", "move:e2:unplaced")
        assertThat(calls.indexOf("move:e1:unplaced")).isLessThan(calls.indexOf("deleteDeck:deck-upper"))
        assertThat(calls.indexOf("move:e2:unplaced")).isLessThan(calls.indexOf("deleteDeck:deck-upper"))
    }

    @Test
    fun `unplaced equipment survives the delete with its plan coordinates`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.equipment.upsertEquipment(
            TestData.equipment(
                id = "e1",
                vesselId = "vessel-1",
                deckId = "deck-upper",
                posX = 0.25f,
                posY = 0.75f,
            ),
        )

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitLoaded()
            viewModel.confirmDeleteDeck("deck-upper")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val survivor = fakes.equipment.equipment.value.getValue("e1")
        assertThat(survivor.deckId).isNull()
        assertThat(survivor.zoneId).isNull()
        assertThat(survivor.deletedAt).isNull()
        assertThat(survivor.posX).isEqualTo(0.25f)
        assertThat(survivor.posY).isEqualTo(0.75f)
        assertThat(fakes.vessels.decks.value).doesNotContainKey("deck-upper")
    }

    @Test
    fun `asking to delete only arms the confirmation`() = runTest {
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))

        val viewModel = viewModel()
        viewModel.askDeleteDeck("deck-upper")
        advanceUntilIdle()

        assertThat(viewModel.deleteTarget.value).isEqualTo("deck-upper")
        assertThat(fakes.vessels.decks.value).containsKey("deck-upper")

        viewModel.cancelDeleteDeck()
        assertThat(viewModel.deleteTarget.value).isNull()
        assertThat(fakes.vessels.decks.value).containsKey("deck-upper")
    }

    // ------------------------------------------------------------------ presets

    @Test
    fun `the preset picker falls back to the six built-ins`() = runTest {
        val viewModel = viewModel()
        viewModel.presets.test {
            assertThat(awaitItem()).hasSize(BuiltInPlanPresets.all.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun draft(name: String) = DeckDraft(
        name = name,
        shortCode = null,
        plan = BuiltInPlanPresets.all.first().plan,
        colorTint = null,
        notes = null,
    )
}

/** Skips the initial loading emission and returns the first loaded state. */
private suspend fun app.cash.turbine.TurbineTestContext<DeckManagerUiState>.awaitLoaded(): DeckManagerUiState {
    var state = awaitItem()
    while (state.isLoading) {
        state = awaitItem()
    }
    return state
}
