package com.deckwatch.feature.vessel

import app.cash.turbine.test
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.vessel.category.CategoryDraft
import com.deckwatch.feature.vessel.category.CategoryManagerViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vessels = FakeVesselRepository(clock = { TestData.referenceMillis })

    private fun viewModel() = CategoryManagerViewModel(vessels)

    @Before
    fun seedVessel() = runTest {
        vessels.upsertVessel(TestData.vessel(id = "vessel-1", isActive = true))
    }

    @Test
    fun `a global category is stored with a null vessel id`() = runTest {
        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitItem()
            viewModel.save(CategoryDraft(name = "Weekly Round", colorArgb = 1, isGlobal = true))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vessels.categories.value.values.single().vesselId).isNull()
    }

    @Test
    fun `a vessel-scoped category carries the resolved vessel id`() = runTest {
        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitItem()
            viewModel.save(CategoryDraft(name = "PSC Focus Items", colorArgb = 1, isGlobal = false))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vessels.categories.value.values.single().vesselId).isEqualTo("vessel-1")
    }

    @Test
    fun `flipping the scope keeps the id so the xref rows survive`() = runTest {
        vessels.upsertCategory(
            TestData.category(id = "cat-1", vesselId = null, name = "Weekly Round", sortOrder = 4),
        )

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            awaitItem()
            viewModel.save(
                CategoryDraft(id = "cat-1", name = "Weekly Round", colorArgb = 9, isGlobal = false),
            )
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        val category = vessels.categories.value.getValue("cat-1")
        assertThat(category.vesselId).isEqualTo("vessel-1")
        assertThat(category.sortOrder).isEqualTo(4)
        assertThat(category.colorArgb).isEqualTo(9)
    }

    @Test
    fun `the list shows global categories alongside the vessel's own`() = runTest {
        vessels.upsertCategory(TestData.category(id = "global", vesselId = null, name = "Weekly", sortOrder = 0))
        vessels.upsertCategory(TestData.category(id = "mine", vesselId = "vessel-1", name = "Mine", sortOrder = 1))
        vessels.upsertCategory(TestData.category(id = "other", vesselId = "vessel-9", name = "Other", sortOrder = 2))

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.categories.map { it.id }).containsExactly("global", "mine").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a vessel-scoped save is refused when no vessel can be resolved`() = runTest {
        vessels.deleteVessel("vessel-1")

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.save(CategoryDraft(name = "Orphan", colorArgb = 1, isGlobal = false))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(vessels.categories.value).isEmpty()
    }

    @Test
    fun `delete is armed then confirmed`() = runTest {
        vessels.upsertCategory(TestData.category(id = "cat-1", vesselId = null, name = "Weekly"))

        val viewModel = viewModel()
        viewModel.bind(null)
        viewModel.askDelete("cat-1")
        advanceUntilIdle()
        assertThat(viewModel.deleteTarget.value).isEqualTo("cat-1")
        assertThat(vessels.categories.value).containsKey("cat-1")

        viewModel.confirmDelete("cat-1")
        advanceUntilIdle()
        assertThat(vessels.categories.value).doesNotContainKey("cat-1")
    }
}
