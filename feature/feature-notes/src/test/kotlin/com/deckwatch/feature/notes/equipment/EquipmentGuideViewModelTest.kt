package com.deckwatch.feature.notes.equipment

import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.TechnicalNote
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.notes.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Browsing the catalogue as a guide rather than as a picker — §9.1. */
class EquipmentGuideViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = FakeRepositories()

    @Test
    fun `the index lists only groups that have types, with their counts`() = runTest {
        seed()
        val viewModel = guideViewModel()

        val state = viewModel.uiState.first { !it.loading }

        assertThat(state.groups.map { it.group })
            .containsExactly(EquipmentGroup.LSA, EquipmentGroup.FFE)
        assertThat(state.groups.first { it.group == EquipmentGroup.LSA }.typeCount).isEqualTo(2)
    }

    @Test
    fun `picking a group narrows the list to that group`() = runTest {
        seed()
        val viewModel = guideViewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.setGroup(EquipmentGroup.LSA)

        val state = viewModel.uiState.first { it.group == EquipmentGroup.LSA }
        assertThat(state.types.map { it.typeKey })
            .containsExactly("LSA_LIFEBUOY_PLAIN", "LSA_LIFEJACKET_ADULT")
    }

    @Test
    fun `search matches the guide text, not only the name`() = runTest {
        seed()
        val viewModel = guideViewModel()
        viewModel.setGroup(EquipmentGroup.FFE)
        viewModel.uiState.first { it.group == EquipmentGroup.FFE }

        // "Storz" appears in a note's bullets and nowhere in any type's name: an officer looking
        // for the coupling should still land on the hydrant page.
        viewModel.setQuery("storz")

        val state = viewModel.uiState.first { it.query == "storz" }
        assertThat(state.types.map { it.typeKey }).containsExactly("FFE_FIRE_HYDRANT")
    }

    @Test
    fun `changing group clears a stale search`() = runTest {
        seed()
        val viewModel = guideViewModel()
        viewModel.setGroup(EquipmentGroup.FFE)
        viewModel.setQuery("storz")
        viewModel.uiState.first { it.query == "storz" }

        viewModel.setGroup(EquipmentGroup.LSA)

        val state = viewModel.uiState.first { it.group == EquipmentGroup.LSA }
        assertThat(state.query).isEmpty()
        assertThat(state.types).hasSize(2)
    }

    @Test
    fun `a type's page carries its guide, its tasks and its rules`() = runTest {
        seed()
        val viewModel = EquipmentTypeDetailViewModel(fakes.reference, fakes.maintenance)

        viewModel.bind("FFE_FIRE_HYDRANT")

        val state = viewModel.uiState.first { it.type != null }
        assertThat(state.type?.technicalNotes?.map { it.heading })
            .containsExactly("Pressure and placement", "The joint the whole system stands on")
        assertThat(state.tasks.map { it.key }).containsExactly("FE_MONTHLY_INSPECTION")
        assertThat(state.cards.map { it.refKey }).containsExactly("SOLAS_II2_10_3")
    }

    @Test
    fun `a task key with no definition is left out rather than shown empty`() = runTest {
        seed()
        val viewModel = EquipmentTypeDetailViewModel(fakes.reference, fakes.maintenance)

        // The lifebuoy references a task nothing defines: a content gap, not a row to render.
        viewModel.bind("LSA_LIFEBUOY_PLAIN")

        val state = viewModel.uiState.first { it.type != null }
        assertThat(state.tasks).isEmpty()
        assertThat(state.cards).isEmpty()
    }

    private suspend fun seed() {
        fakes.reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = "LSA_LIFEBUOY_PLAIN",
                group = EquipmentGroup.LSA,
                subGroup = "PERSONAL",
                nameEn = "Lifebuoy (plain)",
                taskKeys = listOf("LB_NOT_DEFINED"),
                regulationRefs = listOf("NOT_SEEDED"),
            ),
        )
        fakes.reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = "LSA_LIFEJACKET_ADULT",
                group = EquipmentGroup.LSA,
                subGroup = "PERSONAL",
                nameEn = "Lifejacket (adult)",
                taskKeys = emptyList(),
                regulationRefs = emptyList(),
            ),
        )
        fakes.reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = "FFE_FIRE_HYDRANT",
                group = EquipmentGroup.FFE,
                subGroup = "FIRE_MAIN",
                nameEn = "Fire hydrant",
                taskKeys = listOf("FE_MONTHLY_INSPECTION"),
                regulationRefs = listOf("SOLAS_II2_10_3"),
                technicalNotes = listOf(
                    TechnicalNote("Pressure and placement", listOf("Two jets, not from one hydrant.")),
                    TechnicalNote(
                        "The joint the whole system stands on",
                        listOf("Storz is symmetrical: either half mates with the other."),
                    ),
                ),
            ),
        )
        fakes.maintenance.upsertTaskDefinition(TestData.taskDefinition(key = "FE_MONTHLY_INSPECTION"))
        fakes.reference.seedRegulationCard(TestData.regulationCard(refKey = "SOLAS_II2_10_3"))
    }

    private fun guideViewModel() = EquipmentGuideViewModel(fakes.reference)
}
