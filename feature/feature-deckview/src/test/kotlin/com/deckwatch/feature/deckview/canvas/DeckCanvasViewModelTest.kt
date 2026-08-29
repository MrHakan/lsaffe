package com.deckwatch.feature.deckview.canvas

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.deckview.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Placement on the deck canvas — §7.1 A, §7.3. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeckCanvasViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val fakes = FakeRepositories()
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun createFixtures() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
        storeScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
        )
        preferences = UserPreferencesRepository(dataStore)
    }

    @After
    fun releaseFixtures() {
        storeScope.cancel()
    }

    @Test
    fun `the top deck is shown first, because a general arrangement reads downwards`() = runTest {
        seed()
        val viewModel = viewModel()

        val state = viewModel.uiState.first { !it.loading }

        assertThat(state.decks.map { it.id }).containsExactly(BRIDGE_ID, MAIN_ID).inOrder()
        assertThat(state.selectedDeck?.id).isEqualTo(BRIDGE_ID)
    }

    @Test
    fun `only the selected deck's equipment is drawn`() = runTest {
        seed()
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "on-main", vesselId = VESSEL_ID, deckId = MAIN_ID),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "unplaced", vesselId = VESSEL_ID, deckId = null),
        )
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.selectDeck(MAIN_ID)
        val state = viewModel.uiState.first { it.selectedDeck?.id == MAIN_ID }

        assertThat(state.equipment.map { it.id }).containsExactly("on-main")
    }

    @Test
    fun `dropping an item inside a zone puts it in that zone`() = runTest {
        seed()
        fakes.vessels.upsertZone(
            TestData.zone(
                id = ZONE_ID,
                deckId = MAIN_ID,
                polygon = listOf(
                    PlanPoint(0.6f, 0.6f),
                    PlanPoint(0.9f, 0.6f),
                    PlanPoint(0.9f, 0.9f),
                    PlanPoint(0.6f, 0.9f),
                ),
            ),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = EQUIPMENT_ID, vesselId = VESSEL_ID, deckId = MAIN_ID),
        )
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.selectDeck(MAIN_ID)
        viewModel.uiState.first { it.zones.isNotEmpty() }

        viewModel.moveTo(EQUIPMENT_ID, PlanPoint(0.75f, 0.75f))

        val moved = fakes.equipment.getEquipment(EQUIPMENT_ID)
        assertThat(moved?.deckId).isEqualTo(MAIN_ID)
        assertThat(moved?.zoneId).isEqualTo(ZONE_ID)
        assertThat(moved?.posX).isWithin(TOLERANCE).of(0.75f)
    }

    @Test
    fun `dropping outside every zone clears the zone rather than keeping a stale one`() = runTest {
        seed()
        fakes.vessels.upsertZone(
            TestData.zone(
                id = ZONE_ID,
                deckId = MAIN_ID,
                polygon = listOf(
                    PlanPoint(0.6f, 0.6f),
                    PlanPoint(0.9f, 0.6f),
                    PlanPoint(0.9f, 0.9f),
                    PlanPoint(0.6f, 0.9f),
                ),
            ),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = EQUIPMENT_ID, vesselId = VESSEL_ID, deckId = MAIN_ID, zoneId = ZONE_ID),
        )
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.selectDeck(MAIN_ID)
        viewModel.uiState.first { it.zones.isNotEmpty() }

        viewModel.moveTo(EQUIPMENT_ID, PlanPoint(0.1f, 0.1f))

        assertThat(fakes.equipment.getEquipment(EQUIPMENT_ID)?.zoneId).isNull()
    }

    @Test
    fun `grid snap rounds a drop to the grid, and is off by default`() = runTest {
        seed()
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        assertThat(viewModel.snap(PlanPoint(0.333f, 0.777f), enabled = false))
            .isEqualTo(PlanPoint(0.333f, 0.777f))
        assertThat(viewModel.snap(PlanPoint(0.333f, 0.777f), enabled = true))
            .isEqualTo(PlanPoint(0.35f, 0.8f))
    }

    @Test
    fun `a drop off the edge of the deck is pulled back onto it`() = runTest {
        seed()
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading }

        val snapped = viewModel.snap(PlanPoint(-0.4f, 1.9f), enabled = false)

        assertThat(snapped).isEqualTo(PlanPoint(0f, 1f))
    }

    private suspend fun seed() {
        fakes.vessels.upsertVessel(TestData.vessel(id = VESSEL_ID))
        fakes.vessels.setActiveVessel(VESSEL_ID)
        fakes.vessels.upsertDeck(
            TestData.deck(id = MAIN_ID, vesselId = VESSEL_ID, name = "Main Deck", levelIndex = 0),
        )
        fakes.vessels.upsertDeck(
            TestData.deck(id = BRIDGE_ID, vesselId = VESSEL_ID, name = "Bridge Deck", levelIndex = 20),
        )
    }

    private fun viewModel() = DeckCanvasViewModel(
        vesselRepository = fakes.vessels,
        equipmentRepository = fakes.equipment,
        referenceRepository = fakes.reference,
        preferences = preferences,
    )

    private companion object {
        const val VESSEL_ID = "vessel-under-test"
        const val MAIN_ID = "deck-main"
        const val BRIDGE_ID = "deck-bridge"
        const val ZONE_ID = "zone-fwd"
        const val EQUIPMENT_ID = "equipment-under-test"
        const val TOLERANCE = 1e-4f
    }
}
