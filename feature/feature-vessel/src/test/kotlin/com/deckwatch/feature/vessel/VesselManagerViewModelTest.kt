package com.deckwatch.feature.vessel

import app.cash.turbine.test
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.common.ImoStatus
import com.deckwatch.feature.vessel.manager.VesselManagerViewModel
import com.deckwatch.feature.vessel.selector.VesselSelectorViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VesselManagerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vessels = FakeVesselRepository(clock = { TestData.referenceMillis })

    @Test
    fun `an empty repository reports the teaching empty state`() = runTest {
        val viewModel = VesselManagerViewModel(vessels)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.isEmpty).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each row carries its imo validation state`() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "v1", name = "MV Good", imoNumber = "9074729"))
        vessels.upsertVessel(TestData.vessel(id = "v2", name = "MV Wrong", imoNumber = "9074720"))
        vessels.upsertVessel(TestData.vessel(id = "v3", name = "MV None", imoNumber = null))

        val viewModel = VesselManagerViewModel(vessels)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            val byName = state.vessels.associateBy { it.vessel.name }
            assertThat(byName.getValue("MV Good").imoStatus).isEqualTo(ImoStatus.VALID)
            assertThat(byName.getValue("MV Wrong").imoStatus).isEqualTo(ImoStatus.INVALID)
            assertThat(byName.getValue("MV None").imoStatus).isEqualTo(ImoStatus.NOT_ENTERED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setting a vessel active clears the flag on the others`() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "v1", name = "MV First", isActive = true))
        vessels.upsertVessel(TestData.vessel(id = "v2", name = "MV Second", isActive = false))

        val viewModel = VesselManagerViewModel(vessels)
        viewModel.setActive("v2")
        advanceUntilIdle()

        assertThat(vessels.vessels.value.getValue("v1").isActive).isFalse()
        assertThat(vessels.vessels.value.getValue("v2").isActive).isTrue()
    }

    @Test
    fun `delete only happens after the confirmation`() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "v1", name = "MV First"))
        vessels.upsertDeck(TestData.deck(id = "d1", vesselId = "v1"))

        val viewModel = VesselManagerViewModel(vessels)
        viewModel.askDelete("v1")
        advanceUntilIdle()
        assertThat(viewModel.deleteTarget.value).isEqualTo("v1")
        assertThat(vessels.vessels.value).containsKey("v1")

        viewModel.cancelDelete()
        advanceUntilIdle()
        assertThat(vessels.vessels.value).containsKey("v1")

        viewModel.confirmDelete("v1")
        advanceUntilIdle()
        assertThat(vessels.vessels.value).doesNotContainKey("v1")
        // The repository owns the cascade (§6.2 onDelete = CASCADE).
        assertThat(vessels.decks.value).doesNotContainKey("d1")
    }

    @Test
    fun `the selector exposes every vessel and the active one`() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "v1", name = "MV First", isActive = true))
        vessels.upsertVessel(TestData.vessel(id = "v2", name = "MV Second", isActive = false))

        val viewModel = VesselSelectorViewModel(vessels)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.vessels.isEmpty()) state = awaitItem()

            assertThat(state.vessels.map { it.id }).containsExactly("v1", "v2")
            assertThat(state.active?.id).isEqualTo("v1")

            viewModel.select("v2")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vessels.vessels.value.getValue("v2").isActive).isTrue()
    }
}
