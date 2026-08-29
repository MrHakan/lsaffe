package com.deckwatch.feature.vessel

import app.cash.turbine.test
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.deck.BuiltInPlanPresets
import com.deckwatch.feature.vessel.list.VesselListModeViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VesselListModeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = FakeRepositories()

    private fun viewModel() =
        VesselListModeViewModel(fakes.vessels, fakes.equipment, fakes.reference)

    @Test
    fun `list mode follows the active vessel and groups deck then zone then equipment`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", name = "MV Example", isActive = true))
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-2", name = "MT Other", isActive = false))
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-bridge", vesselId = "vessel-1", levelIndex = 20))
        fakes.vessels.upsertZone(
            TestData.zone(id = "z-fwd", deckId = "deck-upper", name = "Fwd Mooring Station", sortOrder = 0),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(
                id = "e1",
                vesselId = "vessel-1",
                deckId = "deck-upper",
                zoneId = "z-fwd",
                tag = "FE-UD-01",
            ),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e2", vesselId = "vessel-1", deckId = "deck-upper", tag = "FE-UD-02"),
        )
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e3", vesselId = "vessel-2", deckId = null, tag = "FE-XX-01"),
        )

        val viewModel = viewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.vessel?.id).isEqualTo("vessel-1")
            assertThat(state.groups.map { it.key })
                .containsExactly("deck-bridge", "deck-upper")
                .inOrder()

            val upper = state.groups.single { it.key == "deck-upper" }
            assertThat(upper.zoneGroups.map { it.zone?.name })
                .containsExactly("Fwd Mooring Station", null)
                .inOrder()
            assertThat(upper.equipmentCount).isEqualTo(2)
            // The other vessel's equipment never appears.
            assertThat(state.groups.flatMap { it.zoneGroups }.flatMap { it.equipment }.map { it.id })
                .containsExactly("e1", "e2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a vessel with no decks reports the first-run empty state`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))

        val viewModel = viewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.hasVessel).isTrue()
            assertThat(state.hasNoDecks).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no active vessel reports no vessel rather than an empty deck list`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.hasVessel).isFalse()
            assertThat(state.hasNoDecks).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a preset in the empty state creates the first deck at level zero`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))
        val preset = BuiltInPlanPresets.all.first()

        val viewModel = viewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.createDeckFromPreset(preset, preset.nameEn)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val deck = fakes.vessels.decks.value.values.single()
        assertThat(deck.name).isEqualTo(preset.nameEn)
        assertThat(deck.levelIndex).isEqualTo(0)
        assertThat(deck.shortCode).isEqualTo(preset.suggestedShortCode)
        assertThat(deck.plan.shape).isEqualTo(preset.plan.shape)
    }

    @Test
    fun `unplaced equipment is still listed`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", vesselId = "vessel-1", deckId = null, tag = "FE-99"),
        )

        val viewModel = viewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.groups.last().isUnplaced).isTrue()
            assertThat(state.groups.last().equipmentCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `equipment type names are made available for the row subtitle`() = runTest {
        fakes.vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))
        fakes.vessels.upsertDeck(TestData.deck(id = "deck-upper", vesselId = "vessel-1", levelIndex = 0))
        fakes.reference.seedEquipmentType(TestData.equipmentType())
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "e1", vesselId = "vessel-1", deckId = "deck-upper"),
        )

        val viewModel = viewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.types.isEmpty()) state = awaitItem()

            assertThat(state.types.keys).contains("FFE_PORTABLE_EXTINGUISHER")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
