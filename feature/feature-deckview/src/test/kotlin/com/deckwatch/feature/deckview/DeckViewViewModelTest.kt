package com.deckwatch.feature.deckview

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanPreset
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The Vessel tab's view model: render-model assembly, per-vessel mode, marker moves and the sweep
 * round of §7.3.
 *
 * Settings come from a real Preferences DataStore over a temporary file, exactly as
 * `core-datastore`'s own tests do, so the isometric angle and grid-snap paths are the real ones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val fakes = FakeRepositories()
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: UserPreferencesRepository

    private var nextId = 0

    @Before
    fun setUp() {
        storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        preferences = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private fun viewModel() = DeckViewViewModel(
        vesselRepository = fakes.vessels,
        equipmentRepository = fakes.equipment,
        inspectionRepository = fakes.inspections,
        referenceRepository = fakes.reference,
        preferences = preferences,
    ).apply {
        today = { TestData.referenceDay }
        clock = { FIXED_MILLIS }
        newId = { "generated-${++nextId}" }
    }

    private suspend fun seedVessel() {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", name = "MV Example", isActive = true))
        fakes.vessels.upsertDeck(
            TestData.deck(id = "upper", vesselId = "vessel-1", levelIndex = 0, shortCode = "UD"),
        )
        fakes.vessels.upsertDeck(
            TestData.deck(id = "bridge", vesselId = "vessel-1", levelIndex = 20, shortCode = "BR"),
        )
        fakes.reference.seedEquipmentType(TestData.equipmentType())
    }

    @Test
    fun `the render model follows the active vessel and ranks decks bottom first`() = runTest {
        seedVessel()
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", vesselId = "vessel-1", deckId = "upper", posX = 0.2f, posY = 0.2f),
        )

        viewModel().uiState.test {
            val state = awaitLoaded()

            assertThat(state.vessel?.id).isEqualTo("vessel-1")
            assertThat(state.model.decks.map { it.deckId })
                .containsExactly("upper", "bridge").inOrder()
            assertThat(state.model.decks.map { it.levelZ }).containsExactly(0, 1).inOrder()
            assertThat(state.model.deck("upper")?.markers?.single()?.typeName)
                .isEqualTo("Portable fire extinguisher")
            assertThat(state.mode).isEqualTo(DeckViewMode.STACK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no vessel yields an empty model that has finished loading`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()

            assertThat(state.hasVessel).isFalse()
            assertThat(state.model.isEmpty).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overdue equipment is counted against today`() = runTest {
        seedVessel()
        fakes.equipment.upsertEquipment(
            TestData.equipment(
                id = "late",
                vesselId = "vessel-1",
                deckId = "upper",
                nextDueDate = TestData.referenceDay - 5,
            ),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(
                id = "fine",
                vesselId = "vessel-1",
                deckId = "upper",
                nextDueDate = TestData.referenceDay + 5,
            ),
        )

        viewModel().uiState.test {
            val state = awaitLoaded()

            assertThat(state.model.deck("upper")?.overdueCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the view mode is remembered per vessel`() = runTest {
        seedVessel()
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.setMode(DeckViewMode.LIST)
            advanceUntilIdle()
            assertThat(expectMostRecentItem().mode).isEqualTo(DeckViewMode.LIST)

            viewModel.enterDeckMode("bridge")
            advanceUntilIdle()
            val deckMode = expectMostRecentItem()
            assertThat(deckMode.mode).isEqualTo(DeckViewMode.DECK)
            assertThat(deckMode.focusedDeckId).isEqualTo("bridge")
            assertThat(deckMode.activeDeck?.deckId).isEqualTo("bridge")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `focus on a deck that has gone away is dropped`() = runTest {
        seedVessel()
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.focusDeck("bridge")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().focusedDeckId).isEqualTo("bridge")

            fakes.vessels.deleteDeck("bridge")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().focusedDeckId).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the isometric angle and grid snap come from settings and are written back clamped`() =
        runTest {
            seedVessel()
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertThat(awaitLoaded().isoAngleDeg).isEqualTo(30f)

                viewModel.setIsoAngle(90f)
                viewModel.setGridSnap(true)
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertThat(state.isoAngleDeg).isEqualTo(35f)
                assertThat(state.gridSnapEnabled).isTrue()
                assertThat(state.effectiveAngleDeg).isEqualTo(35f)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the flat toggle only collapses the projection in deck mode`() = runTest {
        seedVessel()
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.toggleFlat()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().effectiveAngleDeg).isEqualTo(30f)

            viewModel.enterDeckMode("upper")
            advanceUntilIdle()
            assertThat(expectMostRecentItem().effectiveAngleDeg).isEqualTo(0f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving a marker re-infers the zone it was dropped into`() = runTest {
        seedVessel()
        fakes.vessels.upsertZone(
            TestData.zone(
                id = "z-fwd",
                deckId = "upper",
                polygon = listOf(
                    PlanPoint(0f, 0f),
                    PlanPoint(1f, 0f),
                    PlanPoint(1f, 0.5f),
                    PlanPoint(0f, 0.5f),
                ),
            ),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", vesselId = "vessel-1", deckId = "upper", posY = 0.9f),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.moveEquipment("e1", "upper", posX = 0.4f, posY = 0.2f)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val moved = fakes.equipment.getEquipment("e1")
        assertThat(moved?.zoneId).isEqualTo("z-fwd")
        assertThat(moved?.posY).isEqualTo(0.2f)
    }

    @Test
    fun `a sweep writes a round keyed by the deck and opens on the first ungraded item`() = runTest {
        seedVessel()
        fakes.equipment.upsertEquipment(
            TestData.equipment(
                id = "fwd",
                vesselId = "vessel-1",
                deckId = "upper",
                posY = 0.1f,
                condition = ConditionGrade.NOT_CHECKED,
            ),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(
                id = "aft",
                vesselId = "vessel-1",
                deckId = "upper",
                posY = 0.9f,
                condition = ConditionGrade.NOT_CHECKED,
            ),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.startSweep("upper", "Sweep — Upper Deck")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.sweep?.currentEquipmentId).isEqualTo("fwd")
            assertThat(state.focusedDeckId).isEqualTo("upper")

            val round = fakes.inspections.rounds.value.values.single()
            assertThat(round.templateKey).isEqualTo("SWEEP_UD")
            assertThat(round.title).isEqualTo("Sweep — Upper Deck")
            assertThat(round.startedAt).isEqualTo(FIXED_MILLIS)
            assertThat(round.completedAt).isNull()
            assertThat(round.itemCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `grading during a sweep writes a round item and advances, then finishes with the counts`() =
        runTest {
            seedVessel()
            fakes.equipment.upsertEquipment(
                TestData.equipment(id = "fwd", vesselId = "vessel-1", deckId = "upper", posY = 0.1f),
            )
            fakes.equipment.upsertEquipment(
                TestData.equipment(id = "aft", vesselId = "vessel-1", deckId = "upper", posY = 0.9f),
            )
            fakes.equipment.setCondition("fwd", ConditionGrade.NOT_CHECKED, FIXED_MILLIS)
            fakes.equipment.setCondition("aft", ConditionGrade.NOT_CHECKED, FIXED_MILLIS)
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitLoaded()
                viewModel.startSweep("upper", "Sweep — Upper Deck")
                advanceUntilIdle()

                viewModel.onSweepGraded("fwd", ConditionGrade.GOOD)
                advanceUntilIdle()
                assertThat(expectMostRecentItem().sweep?.currentEquipmentId).isEqualTo("aft")

                viewModel.onSweepGraded("aft", ConditionGrade.DEFECTIVE)
                advanceUntilIdle()
                // Nothing left on the deck, so the sweep closes itself (§7.3).
                assertThat(expectMostRecentItem().sweep).isNull()
                cancelAndIgnoreRemainingEvents()
            }

            val roundId = fakes.inspections.rounds.value.values.single().id
            val items = fakes.inspections.observeRoundItems(roundId).first()
            assertThat(items.map { it.equipmentId }).containsExactly("fwd", "aft")
            assertThat(items.map { it.condition })
                .containsExactly(ConditionGrade.GOOD, ConditionGrade.DEFECTIVE)

            val round = fakes.inspections.rounds.value.values.single()
            assertThat(round.completedAt).isEqualTo(FIXED_MILLIS)
            assertThat(round.doneCount).isEqualTo(2)
            assertThat(round.deficiencyCount).isEqualTo(1)
        }

    @Test
    fun `grading the same item twice in one sweep updates its round item rather than duplicating`() =
        runTest {
            seedVessel()
            listOf("a", "b", "c").forEachIndexed { index, id ->
                fakes.equipment.upsertEquipment(
                    TestData.equipment(
                        id = id,
                        vesselId = "vessel-1",
                        deckId = "upper",
                        posY = 0.1f * (index + 1),
                    ),
                )
                fakes.equipment.setCondition(id, ConditionGrade.NOT_CHECKED, FIXED_MILLIS)
            }
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitLoaded()
                viewModel.startSweep("upper", "Sweep")
                advanceUntilIdle()
                viewModel.onSweepGraded("a", ConditionGrade.GOOD)
                advanceUntilIdle()
                viewModel.onSweepGraded("a", ConditionGrade.MONITOR)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            val roundId = fakes.inspections.rounds.value.values.single().id
            val items = fakes.inspections.observeRoundItems(roundId).first()
            assertThat(items).hasSize(1)
            assertThat(items.single().condition).isEqualTo(ConditionGrade.MONITOR)
        }

    @Test
    fun `finishing a sweep from the top bar records what was done`() = runTest {
        seedVessel()
        listOf("a", "b").forEachIndexed { index, id ->
            fakes.equipment.upsertEquipment(
                TestData.equipment(
                    id = id,
                    vesselId = "vessel-1",
                    deckId = "upper",
                    posY = 0.2f * (index + 1),
                ),
            )
            fakes.equipment.setCondition(id, ConditionGrade.NOT_CHECKED, FIXED_MILLIS)
        }
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.startSweep("upper", "Sweep")
            advanceUntilIdle()
            viewModel.onSweepGraded("a", ConditionGrade.OUT_OF_SERVICE)
            advanceUntilIdle()
            viewModel.finishSweep()
            advanceUntilIdle()
            assertThat(expectMostRecentItem().sweep).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        val round = fakes.inspections.rounds.value.values.single()
        assertThat(round.completedAt).isEqualTo(FIXED_MILLIS)
        assertThat(round.itemCount).isEqualTo(2)
        assertThat(round.doneCount).isEqualTo(1)
        assertThat(round.deficiencyCount).isEqualTo(1)
    }

    @Test
    fun `a preset tapped in the empty state creates level zero`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))
        val viewModel = viewModel()

        viewModel.uiState.test {
            val empty = awaitLoaded()
            assertThat(empty.hasNoDecks).isTrue()

            viewModel.createDeckFromPreset(
                preset = PlanPreset(
                    key = "BULKER_MAIN_DECK",
                    nameEn = "Bulker main deck",
                    nameTr = "Dökme yük ana güvertesi",
                    plan = TestData.deckPlan(),
                    suggestedShortCode = "UD",
                ),
                name = "Upper Deck",
            )
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(state.model.decks).hasSize(1)
            assertThat(state.model.decks.single().levelIndex).isEqualTo(0)
            assertThat(state.model.decks.single().shortCode).isEqualTo("UD")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the zone under a plan point is what the add flow files new equipment into`() = runTest {
        seedVessel()
        fakes.vessels.upsertZone(
            TestData.zone(
                id = "z-fwd",
                deckId = "upper",
                polygon = listOf(
                    PlanPoint(0f, 0f),
                    PlanPoint(1f, 0f),
                    PlanPoint(1f, 0.5f),
                    PlanPoint(0f, 0.5f),
                ),
            ),
        )
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitLoaded()
            assertThat(viewModel.zoneAt("upper", 0.5f, 0.2f)).isEqualTo("z-fwd")
            assertThat(viewModel.zoneAt("upper", 0.5f, 0.8f)).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `round item ids are deterministic per round and equipment`() {
        assertThat(DeckViewViewModel.roundItemId("round-1", "e-1")).isEqualTo("round-1:e-1")
        assertThat(DeckViewViewModel.sweepTemplateKey("UD")).isEqualTo("SWEEP_UD")
    }

    private suspend fun TurbineTestContext<DeckViewUiState>.awaitLoaded(): DeckViewUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    private companion object {
        const val FIXED_MILLIS = 1_767_225_600_000L
    }
}
