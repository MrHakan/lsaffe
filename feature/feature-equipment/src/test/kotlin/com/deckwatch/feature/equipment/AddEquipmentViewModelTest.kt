package com.deckwatch.feature.equipment

import app.cash.turbine.test
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.equipment.attributes.AttributeError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The add flow of §7.5: catalogue, tag suggestion, the form, duplicate ×N and the due preview. */
class AddEquipmentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = FakeRepositories()
    private val type = TestData.equipmentType()

    @Test
    fun `the tag is suggested as PREFIX-DECK-NN from the next free number`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.selectType(type.typeKey)

        assertThat(viewModel.uiState.value.form.tag).isEqualTo("FE-UD-01")
        assertThat(viewModel.uiState.value.step).isEqualTo(AddStep.DETAILS)
    }

    @Test
    fun `the suggestion continues the deck's own numbering`() = runTest {
        seed()
        fakes.equipment.upsertEquipment(
            TestData.equipment(id = "existing", vesselId = VESSEL_ID, deckId = DECK_ID, tag = "FE-UD-07"),
        )
        val viewModel = boundViewModel()

        viewModel.selectType(type.typeKey)

        assertThat(viewModel.uiState.value.form.tag).isEqualTo("FE-UD-08")
    }

    @Test
    fun `an unplaced item is numbered without a deck code`() = runTest {
        seed()
        val viewModel = AddEquipmentViewModel(fakes.equipment, fakes.reference, fakes.maintenance, fakes.vessels)
        viewModel.bind(VESSEL_ID, deckId = null, zoneId = null, posX = 0.5f, posY = 0.5f)
        viewModel.uiState.first { it.groups.isNotEmpty() }

        viewModel.selectType(type.typeKey)

        assertThat(viewModel.uiState.value.form.tag).isEqualTo("FE-01")
    }

    @Test
    fun `creating writes the item with the drop point, the type and the attributes`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.selectType(type.typeKey)
        viewModel.setAttribute("extinguishingMedium", "CO2")
        viewModel.updateForm { it.copy(location = "Stbd side, aft of provision crane", maker = "Example Maker") }

        viewModel.create()

        val created = fakes.equipment.observeEquipment(VESSEL_ID).first().single()
        assertThat(created.tag).isEqualTo("FE-UD-01")
        assertThat(created.vesselId).isEqualTo(VESSEL_ID)
        assertThat(created.deckId).isEqualTo(DECK_ID)
        assertThat(created.typeKey).isEqualTo(type.typeKey)
        assertThat(created.symbolKey).isEqualTo(type.symbolKey)
        assertThat(created.posX).isEqualTo(0.4f)
        assertThat(created.posY).isEqualTo(0.6f)
        assertThat(created.location).isEqualTo("Stbd side, aft of provision crane")
        assertThat(created.attributesJson).contains("\"extinguishingMedium\":\"CO2\"")
        assertThat(viewModel.uiState.value.createdIds).containsExactly(created.id)
    }

    @Test
    fun `the new item is scheduled straight away`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.selectType(type.typeKey)
        viewModel.setAttribute("extinguishingMedium", "CO2")

        viewModel.create()

        val created = fakes.equipment.observeEquipment(VESSEL_ID).first().single()
        val instances = fakes.maintenance.observeTaskInstances(created.id).first()
        assertThat(instances.map { it.taskKey })
            .containsAtLeast("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE")
        assertThat(created.nextDueDate).isNotNull()
    }

    @Test
    fun `duplicate x N creates N items with incremented tags in a grid`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.selectType(type.typeKey)
        viewModel.setAttribute("extinguishingMedium", "DRY_POWDER_ABC")
        viewModel.setCopies(3)

        viewModel.create()

        val created = fakes.equipment.observeEquipment(VESSEL_ID).first()
        assertThat(created.map { it.tag }).containsExactly("FE-UD-01", "FE-UD-02", "FE-UD-03")
        assertThat(created.map { it.posX to it.posY }.toSet()).hasSize(3)
        assertThat(viewModel.uiState.value.createdIds).hasSize(3)
    }

    @Test
    fun `the copies stepper is clamped to a sane range`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.setCopies(0)
        assertThat(viewModel.uiState.value.copies).isEqualTo(1)

        viewModel.setCopies(9_999)
        assertThat(viewModel.uiState.value.copies).isEqualTo(AddEquipmentViewModel.MAX_COPIES)
    }

    @Test
    fun `a blank tag blocks creation`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.selectType(type.typeKey)
        viewModel.setAttribute("extinguishingMedium", "CO2")
        viewModel.updateForm { it.copy(tag = "  ") }

        viewModel.create()

        assertThat(viewModel.uiState.value.tagError).isTrue()
        assertThat(fakes.equipment.observeEquipment(VESSEL_ID).first()).isEmpty()
    }

    @Test
    fun `a missing required attribute blocks creation`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.selectType(type.typeKey)

        viewModel.create()

        assertThat(viewModel.uiState.value.attributeErrors["extinguishingMedium"])
            .isEqualTo(AttributeError.REQUIRED)
        assertThat(fakes.equipment.observeEquipment(VESSEL_ID).first()).isEmpty()
    }

    @Test
    fun `the catalogue searches both languages and groups what it finds`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.uiState.test {
            viewModel.setQuery("söndürücü")
            val matched = expectMostRecentItem()
            assertThat(matched.groups.flatMap { it.entries }.map { it.typeKey }).containsExactly(type.typeKey)
            // A search opens the sections it matched, so results are visible without another tap.
            assertThat(matched.expandedGroups).contains(type.group)

            viewModel.setQuery("no such thing")
            assertThat(expectMostRecentItem().groups).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `picking a type puts it at the head of Recent`() = runTest {
        seed()
        val other = TestData.equipmentType(
            typeKey = "LSA_LIFEBUOY",
            nameEn = "Lifebuoy",
            nameTr = "Can simidi",
            defaultTagPrefix = "LB",
            taskKeys = emptyList(),
            attributeSchema = emptyList(),
        )
        fakes.reference.seedEquipmentType(other)
        val viewModel = boundViewModel()

        viewModel.selectType(type.typeKey)
        viewModel.backToCatalogue()
        viewModel.selectType(other.typeKey)

        assertThat(viewModel.uiState.value.recent.map { it.typeKey })
            .containsExactly(other.typeKey, type.typeKey)
            .inOrder()
    }

    @Test
    fun `the due preview follows the form and the attribute values`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.selectType(type.typeKey)

        viewModel.updateForm { it.copy(installedDate = TestData.day(2026, 1, 1)) }
        val monthly = viewModel.uiState.value.duePreview.first { it.taskKey == "FE_MONTHLY_INSPECTION" }
        assertThat(monthly.dueDate).isEqualTo(TestData.day(2026, 2, 1))

        viewModel.setAttribute("extinguishingMedium", "CO2")
        assertThat(viewModel.uiState.value.duePreview.map { it.taskKey })
            .contains("FE_CO2_CYLINDER_WEIGHT_CHECK")
    }

    @Test
    fun `copies are laid out in a grid clamped inside the plan`() {
        val single = AddEquipmentViewModel.gridPositions(1, 0.5f, 0.5f)
        assertThat(single).containsExactly(0.5f to 0.5f)

        val four = AddEquipmentViewModel.gridPositions(4, 0.5f, 0.5f)
        assertThat(four).hasSize(4)
        assertThat(four.toSet()).hasSize(4)

        val edge = AddEquipmentViewModel.gridPositions(9, 0.0f, 1.0f)
        assertThat(edge.all { it.first in 0.02f..0.98f && it.second in 0.02f..0.98f }).isTrue()
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun seed() {
        fakes.vessels.upsertVessel(TestData.vessel(id = VESSEL_ID))
        fakes.vessels.upsertDeck(
            TestData.deck(id = DECK_ID, vesselId = VESSEL_ID, name = "Upper Deck", shortCode = "UD"),
        )
        fakes.reference.seedEquipmentType(type)
        listOf(
            TestData.taskDefinition(key = "FE_MONTHLY_INSPECTION", intervalKind = IntervalKind.MONTHLY),
            TestData.taskDefinition(key = "FE_ANNUAL_SERVICE", intervalKind = IntervalKind.ANNUAL),
            TestData.taskDefinition(key = "FE_CO2_CYLINDER_WEIGHT_CHECK", intervalKind = IntervalKind.BIENNIAL),
        ).forEach { fakes.maintenance.upsertTaskDefinition(it) }
    }

    @Test
    fun `re-binding to another zone of the same deck moves the drop point`() = runTest {
        seed()
        fakes.vessels.upsertZone(TestData.zone(id = ZONE_ID, deckId = DECK_ID, name = "Fwd station"))
        val viewModel = boundViewModel()

        // Adding from a zone row after adding from the deck row: same vessel, same deck, new zone.
        viewModel.bind(VESSEL_ID, DECK_ID, zoneId = ZONE_ID, posX = 0.4f, posY = 0.6f)
        viewModel.selectType(type.typeKey)
        viewModel.setAttribute("extinguishingMedium", "CO2")

        viewModel.create()

        val created = fakes.equipment.observeEquipment(VESSEL_ID).first().single()
        assertThat(created.deckId).isEqualTo(DECK_ID)
        assertThat(created.zoneId).isEqualTo(ZONE_ID)
    }

    private suspend fun boundViewModel(): AddEquipmentViewModel {
        val viewModel = AddEquipmentViewModel(fakes.equipment, fakes.reference, fakes.maintenance, fakes.vessels)
        viewModel.bind(VESSEL_ID, DECK_ID, zoneId = null, posX = 0.4f, posY = 0.6f)
        viewModel.uiState.first { it.groups.isNotEmpty() }
        return viewModel
    }

    private companion object {
        const val VESSEL_ID = "vessel-under-test"
        const val DECK_ID = "deck-under-test"
        const val ZONE_ID = "zone-under-test"
    }
}
