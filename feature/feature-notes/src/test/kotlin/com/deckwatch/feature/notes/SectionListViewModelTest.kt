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

/** Section lists, the FLAG sub-lists and in-memory favourites — §8.1, §8.2. */
class SectionListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reference = FakeReferenceRepository()

    @Before
    fun seed() {
        reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = "FFE_PORTABLE_EXTINGUISHER",
                nameEn = "Portable fire extinguisher",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "SOLAS_II2_10_3",
                section = RegulationSection.SOLAS,
                citation = "SOLAS II-2/10.3",
                appliesToTypeKeys = listOf("FFE_PORTABLE_EXTINGUISHER", "FFE_FIRE_HOSE"),
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "LSA_CH4",
                section = RegulationSection.LSA,
                citation = "LSA Code IV",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "FLAG_RMI_2_011_37",
                section = RegulationSection.FLAG,
                citation = "MN 2-011-37",
                flagNotes = mapOf("RMI" to "RO surveyor in attendance for the 5-yearly test."),
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "FLAG_LIB_SAF_005",
                section = RegulationSection.FLAG,
                citation = "SAF-005",
            ),
        )
        reference.seedRegulationCard(
            TestData.regulationCard(
                refKey = "FLAG_GENERAL_GUIDANCE",
                section = RegulationSection.FLAG,
                citation = "Flag guidance",
                sourceRef = "Company circular",
            ),
        )
    }

    @Test
    fun `a non-flag section is one flat group of its own cards`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.SOLAS)

        viewModel.uiState.test {
            val state = awaitState { it.section == RegulationSection.SOLAS && it.cardCount > 0 }

            assertThat(state.groups).hasSize(1)
            assertThat(state.groups.single().flag).isNull()
            assertThat(state.groups.single().cards.map { it.refKey }).containsExactly("SOLAS_II2_10_3")
            assertThat(state.showsFlagSubSections).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `FLAG splits into RMI, Liberia and Panama sub-lists with a catch-all for the rest`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.FLAG)

        viewModel.uiState.test {
            val state = awaitState { it.section == RegulationSection.FLAG && it.cardCount == 3 }

            assertThat(state.showsFlagSubSections).isTrue()
            assertThat(state.groups.map { it.flag })
                .containsExactly(FlagSubSection.RMI, FlagSubSection.LIBERIA, null)
                .inOrder()
            assertThat(state.availableFlags)
                .containsExactly(FlagSubSection.RMI, FlagSubSection.LIBERIA)
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a flag sub-list narrows to that Administration only`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.FLAG)

        viewModel.uiState.test {
            awaitState { it.cardCount == 3 }
            viewModel.setFlagFilter(FlagSubSection.LIBERIA)

            val state = awaitState { it.flagFilter == FlagSubSection.LIBERIA }
            assertThat(state.groups).hasSize(1)
            assertThat(state.groups.single().cards.map { it.refKey }).containsExactly("FLAG_LIB_SAF_005")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing section clears the flag sub-list selection`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.FLAG)
        viewModel.setFlagFilter(FlagSubSection.RMI)

        viewModel.uiState.test {
            awaitState { it.flagFilter == FlagSubSection.RMI }
            viewModel.setSection(RegulationSection.LSA)

            val state = awaitState { it.section == RegulationSection.LSA }
            assertThat(state.flagFilter).isNull()
            assertThat(state.availableFlags).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favourites toggle in memory and report their new state`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.SOLAS)

        viewModel.uiState.test {
            awaitState { it.cardCount == 1 }

            assertThat(viewModel.toggleFavourite("SOLAS_II2_10_3")).isTrue()
            assertThat(awaitState { it.isFavourite("SOLAS_II2_10_3") }.favourites)
                .containsExactly("SOLAS_II2_10_3")

            assertThat(viewModel.toggleFavourite("SOLAS_II2_10_3")).isFalse()
            assertThat(awaitState { it.favourites.isEmpty() }.favourites).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applies-to keys resolve to catalogue names and fall back to the raw key`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.SOLAS)

        viewModel.uiState.test {
            val state = awaitState { it.typeNames.isNotEmpty() && it.cardCount == 1 }
            val card = state.groups.single().cards.single()

            assertThat(state.appliesToNames(card))
                .containsExactly("Portable fire extinguisher", "FFE_FIRE_HOSE")
                .inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty section reports empty rather than an empty group`() = runTest {
        val viewModel = SectionListViewModel(reference)
        viewModel.setSection(RegulationSection.CLASS)

        viewModel.uiState.test {
            val state = awaitState { it.section == RegulationSection.CLASS }
            assertThat(state.groups).isEmpty()
            assertThat(state.isEmpty).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
