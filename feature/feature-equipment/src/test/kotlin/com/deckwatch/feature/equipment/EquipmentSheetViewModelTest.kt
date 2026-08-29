package com.deckwatch.feature.equipment

import app.cash.turbine.test
import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.FakeRepositories
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The quick action (§7.3), the checklist completion (§9.3) and the attribute writes (§7.4). */
class EquipmentSheetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = FakeRepositories()

    private val type = TestData.equipmentType(
        attributeSchema = listOf(
            TestData.attributeDefinition(),
            checkboxAttribute("accessUnobstructed"),
            checkboxAttribute("sealIntact"),
        ),
        taskKeys = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
    )

    @Test
    fun `grading writes the grade and calls the sweep hook`() = runTest {
        seed()
        val viewModel = boundViewModel()
        var graded: Pair<String, ConditionGrade>? = null

        viewModel.setCondition(ConditionGrade.GOOD) { id, grade -> graded = id to grade }

        val stored = fakes.equipment.getEquipment(EQUIPMENT_ID)
        assertThat(stored?.condition).isEqualTo(ConditionGrade.GOOD)
        assertThat(stored?.conditionSetAt).isNotNull()
        assertThat(graded).isEqualTo(EQUIPMENT_ID to ConditionGrade.GOOD)
    }

    @Test
    fun `defective offers a minor deficiency, pre-filled from the item`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.setCondition(ConditionGrade.DEFECTIVE)

        val draft = viewModel.uiState.value.deficiencyDraft
        assertThat(draft?.severity).isEqualTo(Severity.MINOR)
        assertThat(draft?.equipmentId).isEqualTo(EQUIPMENT_ID)
        assertThat(draft?.vesselId).isEqualTo(VESSEL_ID)
        assertThat(draft?.raisedDate).isEqualTo(Dates.todayEpochDay())
        assertThat(draft?.title).contains("FE-UD-01")
    }

    @Test
    fun `out of service suggests a major deficiency`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.setCondition(ConditionGrade.OUT_OF_SERVICE)

        assertThat(viewModel.uiState.value.deficiencyDraft?.severity).isEqualTo(Severity.MAJOR)
    }

    @Test
    fun `a serviceable grade raises nothing`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.setCondition(ConditionGrade.MONITOR)

        assertThat(viewModel.uiState.value.deficiencyDraft).isNull()
        assertThat(DeficiencyDraft.suggestedSeverity(ConditionGrade.MONITOR)).isNull()
    }

    @Test
    fun `dismissing the deficiency form leaves the grade written`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.setCondition(ConditionGrade.DEFECTIVE)

        viewModel.dismissDeficiency()

        assertThat(viewModel.uiState.value.deficiencyDraft).isNull()
        assertThat(fakes.equipment.getEquipment(EQUIPMENT_ID)?.condition).isEqualTo(ConditionGrade.DEFECTIVE)
        assertThat(fakes.inspections.deficiencies.value).isEmpty()
    }

    @Test
    fun `saving the deficiency writes an open record against the item`() = runTest {
        seed()
        val viewModel = boundViewModel()
        viewModel.setCondition(ConditionGrade.OUT_OF_SERVICE)
        viewModel.updateDeficiencyDescription("Gauge below the green band; landed for service.")
        viewModel.updateDeficiencyRaisedBy("3/O")

        viewModel.saveDeficiency()

        val saved = fakes.inspections.deficiencies.value.values.single()
        assertThat(saved.equipmentId).isEqualTo(EQUIPMENT_ID)
        assertThat(saved.vesselId).isEqualTo(VESSEL_ID)
        assertThat(saved.severity).isEqualTo(Severity.MAJOR)
        assertThat(saved.status).isEqualTo(DeficiencyStatus.OPEN)
        assertThat(saved.raisedBy).isEqualTo("3/O")
        assertThat(viewModel.uiState.value.message).isEqualTo(SheetMessage.DEFICIENCY_SAVED)
    }

    @Test
    fun `undo puts the previous grade back with its original timestamp`() = runTest {
        seed(condition = ConditionGrade.ACCEPTABLE)
        val original = fakes.equipment.getEquipment(EQUIPMENT_ID)
        val viewModel = boundViewModel()

        viewModel.setCondition(ConditionGrade.OUT_OF_SERVICE)
        viewModel.undoCondition()

        val restored = fakes.equipment.getEquipment(EQUIPMENT_ID)
        assertThat(restored?.condition).isEqualTo(ConditionGrade.ACCEPTABLE)
        assertThat(restored?.conditionSetAt).isEqualTo(original?.conditionSetAt)
        assertThat(viewModel.uiState.value.conditionUndo).isNull()
        assertThat(viewModel.uiState.value.deficiencyDraft).isNull()
    }

    @Test
    fun `a full monthly checklist completes the type's monthly task`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.toggleChecklistItem("accessUnobstructed", true)
        viewModel.toggleChecklistItem("sealIntact", true)

        val ready = viewModel.uiState.first { it.checklistComplete }
        assertThat(ready.monthlyTaskKey).isEqualTo("FE_MONTHLY_INSPECTION")

        viewModel.logMonthlyInspection()

        val completed = fakes.maintenance.instances.value.values
            .filter { it.taskKey == "FE_MONTHLY_INSPECTION" && it.status == TaskStatus.DONE }
        assertThat(completed).hasSize(1)
        assertThat(completed.single().completedDate).isEqualTo(Dates.todayEpochDay())
        assertThat(completed.single().equipmentId).isEqualTo(EQUIPMENT_ID)
        assertThat(viewModel.uiState.value.message).isEqualTo(SheetMessage.MONTHLY_LOGGED)
    }

    @Test
    fun `a half-ticked checklist offers no completion`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.toggleChecklistItem("accessUnobstructed", true)

        val state = viewModel.uiState.value
        assertThat(state.checklist.count { it.checked }).isEqualTo(1)
        assertThat(state.checklistComplete).isFalse()
    }

    @Test
    fun `a type with no monthly task says so instead of completing something else`() = runTest {
        seed(taskKeys = listOf("FE_ANNUAL_SERVICE"))
        val viewModel = boundViewModel()

        viewModel.logMonthlyInspection()

        assertThat(viewModel.uiState.value.message).isEqualTo(SheetMessage.MONTHLY_NO_TASK)
        assertThat(fakes.maintenance.instances.value.values.none { it.status == TaskStatus.DONE }).isTrue()
    }

    @Test
    fun `saving attributes persists the json and recomputes the schedule`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.startEditingAttributes()
        viewModel.updateAttribute("extinguishingMedium", "CO2")
        viewModel.saveAttributes()

        val stored = fakes.equipment.getEquipment(EQUIPMENT_ID)
        assertThat(stored?.attributesJson).contains("\"extinguishingMedium\":\"CO2\"")
        assertThat(viewModel.uiState.value.editor).isNull()
        assertThat(viewModel.uiState.value.message).isEqualTo(SheetMessage.ATTRIBUTES_SAVED)
        // §9.3: an affectsTasks value re-derives the task set, and the repository re-schedules it.
        assertThat(fakes.maintenance.instances.value.values.map { it.taskKey })
            .contains("FE_CO2_CYLINDER_WEIGHT_CHECK")
    }

    @Test
    fun `a missing required attribute blocks the save and keeps the editor open`() = runTest {
        seed()
        val viewModel = boundViewModel()
        val before = fakes.equipment.getEquipment(EQUIPMENT_ID)?.attributesJson

        viewModel.startEditingAttributes()
        viewModel.updateAttribute("extinguishingMedium", "")
        viewModel.saveAttributes()

        assertThat(viewModel.uiState.value.editor?.errors?.keys).containsExactly("extinguishingMedium")
        assertThat(fakes.equipment.getEquipment(EQUIPMENT_ID)?.attributesJson).isEqualTo(before)
    }

    @Test
    fun `delete is a soft delete that the caller can undo`() = runTest {
        seed()
        val viewModel = boundViewModel()
        var undo: (suspend () -> Unit)? = null

        viewModel.delete { _, action -> undo = action }

        assertThat(fakes.equipment.observeEquipment(VESSEL_ID).first()).isEmpty()
        assertThat(fakes.equipment.getEquipment(EQUIPMENT_ID)?.deletedAt).isNotNull()

        requireNotNull(undo).invoke()

        assertThat(fakes.equipment.observeEquipment(VESSEL_ID).first()).hasSize(1)
        assertThat(fakes.equipment.getEquipment(EQUIPMENT_ID)?.deletedAt).isNull()
    }

    @Test
    fun `duplicate writes the requested number of copies`() = runTest {
        seed()
        val viewModel = boundViewModel()

        viewModel.duplicate(2)

        val tags = fakes.equipment.observeEquipment(VESSEL_ID).first().map { it.tag }
        assertThat(tags).hasSize(3)
        assertThat(tags.toSet()).hasSize(3)
        assertThat(viewModel.uiState.value.message).isEqualTo(SheetMessage.DUPLICATED)
    }

    @Test
    fun `the sheet surfaces the type's regulation cards and its task list`() = runTest {
        seed()
        val viewModel = EquipmentSheetViewModel(
            equipmentRepository = fakes.equipment,
            vesselRepository = fakes.vessels,
            referenceRepository = fakes.reference,
            maintenanceRepository = fakes.maintenance,
            inspectionRepository = fakes.inspections,
        )

        viewModel.uiState.test {
            viewModel.bind(EQUIPMENT_ID)
            var state = awaitItem()
            while (state.equipment == null) state = awaitItem()

            assertThat(state.requirements.map { it.refKey }).containsExactly("SOLAS_II2_10_3")
            assertThat(state.tasks.map { it.taskKey })
                .containsExactly("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE")
            assertThat(state.checklist.map { it.key })
                .containsExactly("accessUnobstructed", "sealIntact")
            assertThat(state.loading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the move picker offers the vessel's decks and the move lands the item`() = runTest {
        seed()
        val viewModel = boundViewModel()

        // What the picker lists: the decks of the vessel this item belongs to.
        assertThat(viewModel.decks.first { it.isNotEmpty() }.map { it.id }).containsExactly(DECK_ID)

        // Landing an item for service takes it off the deck without deleting it — §6.5.
        viewModel.moveToDeck(null)
        assertThat(fakes.equipment.getEquipment(EQUIPMENT_ID)?.deckId).isNull()

        // ...and it goes back onto a deck the same way.
        viewModel.moveToDeck(DECK_ID)
        val placed = fakes.equipment.getEquipment(EQUIPMENT_ID)
        assertThat(placed?.deckId).isEqualTo(DECK_ID)
        assertThat(placed?.zoneId).isNull()
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun seed(
        condition: ConditionGrade = ConditionGrade.NOT_CHECKED,
        taskKeys: List<String> = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
    ) {
        fakes.seed(
            vessel = TestData.vessel(id = VESSEL_ID),
            decks = listOf(TestData.deck(id = DECK_ID, vesselId = VESSEL_ID)),
            types = listOf(type.copy(taskKeys = taskKeys)),
            definitions = listOf(
                TestData.taskDefinition(key = "FE_MONTHLY_INSPECTION", intervalKind = IntervalKind.MONTHLY),
                TestData.taskDefinition(key = "FE_ANNUAL_SERVICE", intervalKind = IntervalKind.ANNUAL),
                TestData.taskDefinition(
                    key = "FE_CO2_CYLINDER_WEIGHT_CHECK",
                    intervalKind = IntervalKind.MONTHLY,
                ),
            ),
            equipmentItems = listOf(
                TestData.equipment(
                    id = EQUIPMENT_ID,
                    vesselId = VESSEL_ID,
                    deckId = DECK_ID,
                    tag = "FE-UD-01",
                    condition = condition,
                ),
            ),
        )
        fakes.reference.seedRegulationCard(TestData.regulationCard(refKey = "SOLAS_II2_10_3"))
    }

    private suspend fun boundViewModel(): EquipmentSheetViewModel {
        val viewModel = EquipmentSheetViewModel(
            equipmentRepository = fakes.equipment,
            vesselRepository = fakes.vessels,
            referenceRepository = fakes.reference,
            maintenanceRepository = fakes.maintenance,
            inspectionRepository = fakes.inspections,
        )
        viewModel.bind(EQUIPMENT_ID)
        viewModel.uiState.first { it.equipment != null }
        return viewModel
    }

    private fun checkboxAttribute(key: String) = AttributeDefinition(
        key = key,
        kind = AttributeKind.BOOLEAN,
        labelEn = key,
        monthlyChecklist = true,
    )

    private companion object {
        const val VESSEL_ID = "vessel-under-test"
        const val DECK_ID = "deck-under-test"
        const val EQUIPMENT_ID = "equipment-under-test"
    }
}
