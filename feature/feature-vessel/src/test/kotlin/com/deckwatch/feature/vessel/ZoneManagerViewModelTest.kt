package com.deckwatch.feature.vessel

import app.cash.turbine.test
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.zone.ZoneDraft
import com.deckwatch.feature.vessel.zone.ZoneGeometry
import com.deckwatch.feature.vessel.zone.ZoneManagerViewModel
import com.deckwatch.feature.vessel.zone.ZoneRect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZoneManagerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vessels = FakeVesselRepository(clock = { TestData.referenceMillis })

    private fun viewModel() = ZoneManagerViewModel(vessels)

    @Before
    fun seedDeck() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))
        vessels.upsertDeck(TestData.deck(id = "deck-1", vesselId = "vessel-1", levelIndex = 0))
    }

    @Test
    fun `a saved zone is stored as a four-point polygon`() = runTest {
        val viewModel = viewModel()
        viewModel.bind("deck-1")
        viewModel.uiState.test {
            awaitItem()
            viewModel.save(
                ZoneDraft(
                    name = "Fwd Mooring Station",
                    colorArgb = 0xFF1F7A75.toInt(),
                    rect = ZoneRect(0.1f, 0.05f, 0.9f, 0.35f),
                ),
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val zone = vessels.zones.value.values.single()
        assertThat(zone.deckId).isEqualTo("deck-1")
        assertThat(zone.name).isEqualTo("Fwd Mooring Station")
        assertThat(zone.polygon).containsExactly(
            PlanPoint(0.1f, 0.05f),
            PlanPoint(0.9f, 0.05f),
            PlanPoint(0.9f, 0.35f),
            PlanPoint(0.1f, 0.35f),
        ).inOrder()
    }

    @Test
    fun `new zones take the next sort order`() = runTest {
        val viewModel = viewModel()
        viewModel.bind("deck-1")
        viewModel.uiState.test {
            awaitItem()
            viewModel.save(ZoneDraft(name = "Pump Room", colorArgb = 1, rect = ZoneGeometry.Default))
            advanceUntilIdle()
            viewModel.save(ZoneDraft(name = "Galley", colorArgb = 2, rect = ZoneGeometry.Default))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val byName = vessels.zones.value.values.associateBy { it.name }
        assertThat(byName.getValue("Pump Room").sortOrder).isEqualTo(0)
        assertThat(byName.getValue("Galley").sortOrder).isEqualTo(1)
    }

    @Test
    fun `editing a zone keeps its id and sort order and rewrites the polygon`() = runTest {
        vessels.upsertZone(
            TestData.zone(
                id = "zone-1",
                deckId = "deck-1",
                name = "Galley",
                polygon = ZoneGeometry.rectToPolygon(ZoneGeometry.Default),
                sortOrder = 3,
            ),
        )

        val viewModel = viewModel()
        viewModel.bind("deck-1")
        viewModel.uiState.test {
            awaitItem()
            viewModel.save(
                ZoneDraft(
                    id = "zone-1",
                    name = "Galley and Pantry",
                    colorArgb = 7,
                    rect = ZoneRect(0.0f, 0.0f, 0.5f, 0.5f),
                ),
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val zone = vessels.zones.value.getValue("zone-1")
        assertThat(zone.name).isEqualTo("Galley and Pantry")
        assertThat(zone.sortOrder).isEqualTo(3)
        assertThat(zone.polygon).hasSize(4)
        assertThat(zone.polygon.first()).isEqualTo(PlanPoint(0f, 0f))
    }

    @Test
    fun `move up swaps a zone with the one above it`() = runTest {
        vessels.upsertZone(TestData.zone(id = "z1", deckId = "deck-1", name = "A", sortOrder = 0))
        vessels.upsertZone(TestData.zone(id = "z2", deckId = "deck-1", name = "B", sortOrder = 1))

        val viewModel = viewModel()
        viewModel.bind("deck-1")
        viewModel.uiState.test {
            awaitItem()
            viewModel.moveUp("z2")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vessels.observeZones("deck-1").first().map { it.id })
            .containsExactly("z2", "z1")
            .inOrder()
    }

    @Test
    fun `move down at the bottom is a no-op`() = runTest {
        vessels.upsertZone(TestData.zone(id = "z1", deckId = "deck-1", name = "A", sortOrder = 0))
        vessels.upsertZone(TestData.zone(id = "z2", deckId = "deck-1", name = "B", sortOrder = 1))

        val viewModel = viewModel()
        viewModel.bind("deck-1")
        viewModel.uiState.test {
            awaitItem()
            viewModel.moveDown("z2")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vessels.observeZones("deck-1").first().map { it.id })
            .containsExactly("z1", "z2")
            .inOrder()
    }

    @Test
    fun `delete is armed by ask and only bites on confirm`() = runTest {
        vessels.upsertZone(TestData.zone(id = "z1", deckId = "deck-1", name = "A", sortOrder = 0))

        val viewModel = viewModel()
        viewModel.bind("deck-1")
        viewModel.askDelete("z1")
        advanceUntilIdle()
        assertThat(viewModel.deleteTarget.value).isEqualTo("z1")
        assertThat(vessels.zones.value).containsKey("z1")

        viewModel.confirmDelete("z1")
        advanceUntilIdle()
        assertThat(vessels.zones.value).doesNotContainKey("z1")
    }

    @Test
    fun `the deck being edited is exposed for the outline preview`() = runTest {
        val viewModel = viewModel()
        viewModel.bind("deck-1")
        advanceUntilIdle()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.deck == null) state = awaitItem()
            assertThat(state.deck?.id).isEqualTo("deck-1")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
