package com.deckwatch.feature.notes

import app.cash.turbine.test
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.testing.FakeReferenceRepository
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Section counts and global search on the top level of the Notes tab — §8.1. */
class NotesHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reference = FakeReferenceRepository()

    @Before
    fun seed() {
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "SOLAS_III_20_6",
                section = RegulationSection.SOLAS,
                citation = "SOLAS III/20.6",
                title = "Weekly inspection of survival craft",
                what = "Survival craft and launching appliances are inspected weekly.",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "SOLAS_II2_14",
                section = RegulationSection.SOLAS,
                citation = "SOLAS II-2/14",
                title = "Operational readiness and maintenance",
                what = "A maintenance plan for fire protection systems is kept on board.",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "LSA_CH4",
                section = RegulationSection.LSA,
                citation = "LSA Code IV",
                title = "Survival craft",
                what = "Requirements for lifeboats and liferafts.",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "FLAG_RMI_2_011_37",
                section = RegulationSection.FLAG,
                citation = "MN 2-011-37",
                title = "Life-saving appliances and systems",
                what = "RMI requirements over and above SOLAS III.",
            ),
        )
    }

    @Test
    fun `section counts come from the bundled cards and MY NOTES from the user's own`() = runTest {
        reference.upsertUserNote(TestData.userNote(id = "note-a"))
        reference.upsertUserNote(TestData.userNote(id = "note-b"))
        val viewModel = NotesHomeViewModel(reference)

        viewModel.uiState.test {
            val state = awaitState { it.countFor(RegulationSection.SOLAS) == 2 }

            assertThat(state.countFor(RegulationSection.SOLAS)).isEqualTo(2)
            assertThat(state.countFor(RegulationSection.LSA)).isEqualTo(1)
            assertThat(state.countFor(RegulationSection.FFE)).isEqualTo(0)
            assertThat(state.countFor(RegulationSection.FLAG)).isEqualTo(1)
            assertThat(state.countFor(RegulationSection.CLASS)).isEqualTo(0)
            assertThat(state.countFor(RegulationSection.MY_NOTES)).isEqualTo(2)
            assertThat(state.isSearching).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank query shows no results and is not a search`() = runTest {
        val viewModel = NotesHomeViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.countFor(RegulationSection.SOLAS) == 2 }
            viewModel.onQueryChange("   ")

            val state = awaitState { it.query == "   " }
            assertThat(state.isSearching).isFalse()
            assertThat(state.results).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search matches the citation, the title and the WHAT statement`() = runTest {
        val viewModel = NotesHomeViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.countFor(RegulationSection.SOLAS) == 2 }

            viewModel.onQueryChange("II-2/14")
            assertThat(awaitState { it.results.isNotEmpty() }.results.map { it.refKey })
                .containsExactly("SOLAS_II2_14")

            viewModel.onQueryChange("survival craft")
            assertThat(awaitState { it.results.size == 2 }.results.map { it.refKey })
                .containsExactly("SOLAS_III_20_6", "LSA_CH4")

            viewModel.onQueryChange("liferafts")
            assertThat(awaitState { it.results.size == 1 }.results.map { it.refKey })
                .containsExactly("LSA_CH4")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search is case-insensitive and a miss returns nothing`() = runTest {
        val viewModel = NotesHomeViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.countFor(RegulationSection.SOLAS) == 2 }

            viewModel.onQueryChange("WEEKLY")
            assertThat(awaitState { it.results.isNotEmpty() }.results.map { it.refKey })
                .containsExactly("SOLAS_III_20_6")

            viewModel.onQueryChange("hovercraft")
            val miss = awaitState { it.query == "hovercraft" && it.results.isEmpty() }
            assertThat(miss.isSearching).isTrue()
            assertThat(miss.results).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the query leaves search mode`() = runTest {
        val viewModel = NotesHomeViewModel(reference)

        viewModel.uiState.test {
            awaitState { it.countFor(RegulationSection.SOLAS) == 2 }
            viewModel.onQueryChange("weekly")
            awaitState { it.results.isNotEmpty() }

            viewModel.clearQuery()
            val cleared = awaitState { !it.isSearching }
            assertThat(cleared.results).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
