package com.deckwatch.feature.inspection

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.FakeEquipmentRepository
import com.deckwatch.core.testing.FakeMaintenanceRepository
import com.deckwatch.core.testing.FakeReferenceRepository
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Due tab's join, bucketing, filtering, survey prep and writes — §12.
 *
 * "Today" is injected as a fixed epoch-day, so the boundaries never drift with the wall clock.
 */
class DueViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = TestData.referenceDay
    private val certExpiry = today + 100

    private val vessels = FakeVesselRepository()
    private val equipment = FakeEquipmentRepository()
    private val reference = FakeReferenceRepository()
    private val maintenanceFake = FakeMaintenanceRepository(
        equipment = equipment,
        reference = reference,
        vessels = vessels,
        today = { today },
        clock = { TestData.referenceMillis },
    )
    private val maintenance = RecordingMaintenanceRepository(maintenanceFake)

    private fun viewModel() = DueViewModel(
        vesselRepository = vessels,
        equipmentRepository = equipment,
        maintenanceRepository = maintenance,
        referenceRepository = reference,
        today = { today },
        clock = { TestData.referenceMillis },
    )

    @Before
    fun seed() {
        val vessel = TestData.vessel(
            id = VESSEL,
            name = "MV Example",
            imoNumber = "9074729",
            safetyEquipmentCertExpiry = certExpiry,
            isActive = true,
        )
        vessels.vessels.value = mapOf(vessel.id to vessel)
        val upper = TestData.deck(id = "deck-upper", vesselId = VESSEL, name = "Upper Deck", shortCode = "UD", levelIndex = 0)
        val boat = TestData.deck(id = "deck-boat", vesselId = VESSEL, name = "Boat Deck", shortCode = "BD", levelIndex = 10)
        vessels.decks.value = mapOf(upper.id to upper, boat.id to boat)
        vessels.categories.value = mapOf(
            CATEGORY to TestData.category(id = CATEGORY, vesselId = VESSEL, name = "Weekly Round"),
        )

        reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = EXTINGUISHER,
                group = EquipmentGroup.FFE,
                taskKeys = listOf(MONTHLY_TASK),
            ),
        )
        reference.seedEquipmentType(
            TestData.equipmentType(
                typeKey = LIFEBOAT,
                group = EquipmentGroup.LSA,
                nameEn = "Lifeboat",
                nameTr = "Filika",
                symbolKey = "LSS001",
                defaultTagPrefix = "LB",
                attributeSchema = emptyList(),
                taskKeys = listOf(ANNUAL_TASK),
            ),
        )

        maintenanceFake.definitions.value = mapOf(
            MONTHLY_TASK to TestData.taskDefinition(
                key = MONTHLY_TASK,
                appliesToTypeKeys = listOf(EXTINGUISHER),
                titleEn = "Portable fire extinguisher — monthly check",
                titleTr = "Portatif yangın söndürücü — aylık kontrol",
                performedBy = PerformedBy.SHIP_STAFF,
            ),
            ANNUAL_TASK to TestData.taskDefinition(
                key = ANNUAL_TASK,
                appliesToTypeKeys = listOf(LIFEBOAT),
                titleEn = "Lifeboat — annual thorough examination",
                titleTr = "Filika — yıllık detaylı muayene",
                intervalKind = IntervalKind.ANNUAL,
                performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
            ),
        )

        val extinguisher = TestData.equipment(
            id = FE,
            vesselId = VESSEL,
            deckId = "deck-upper",
            zoneId = "zone-fwd",
            typeKey = EXTINGUISHER,
            symbolKey = "FES001",
            tag = "FE-UD-01",
            condition = ConditionGrade.GOOD,
        )
        val lifeboat = TestData.equipment(
            id = LB,
            vesselId = VESSEL,
            deckId = "deck-boat",
            typeKey = LIFEBOAT,
            symbolKey = "LSS001",
            tag = "LB-01",
            condition = ConditionGrade.MONITOR,
            attributesJson = "{}",
        )
        equipment.equipment.value = mapOf(extinguisher.id to extinguisher, lifeboat.id to lifeboat)
        equipment.categoryXref.value = mapOf(FE to listOf(CATEGORY))

        maintenanceFake.instances.value = listOf(
            TestData.taskInstance(
                id = "i-overdue",
                equipmentId = FE,
                taskKey = MONTHLY_TASK,
                dueDate = today - 40,
                windowCloses = today - 40,
                status = TaskStatus.OVERDUE,
            ),
            TestData.taskInstance(
                id = "i-week",
                equipmentId = FE,
                taskKey = MONTHLY_TASK,
                dueDate = today + 3,
                windowCloses = today + 3,
                status = TaskStatus.DUE_SOON,
            ),
            TestData.taskInstance(
                id = "i-month",
                equipmentId = LB,
                taskKey = ANNUAL_TASK,
                dueDate = today + 20,
                windowCloses = today + 20,
                status = TaskStatus.DUE_SOON,
            ),
            TestData.taskInstance(
                id = "i-survey",
                equipmentId = LB,
                taskKey = ANNUAL_TASK,
                dueDate = today + 60,
                windowCloses = today + 60,
                status = TaskStatus.PENDING,
            ),
            TestData.taskInstance(
                id = "i-planned",
                equipmentId = FE,
                taskKey = MONTHLY_TASK,
                dueDate = certExpiry + 30,
                windowCloses = certExpiry + 30,
                status = TaskStatus.PENDING,
            ),
        ).associateBy { it.id }
    }

    @Test
    fun `open work is bucketed into the five segments`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertThat(state.vesselName).isEqualTo("MV Example")
            assertThat(state.certExpiry).isEqualTo(certExpiry)
            assertThat(state.countOf(DueSegment.OVERDUE)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.THIS_WEEK)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.THIS_MONTH)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.BEFORE_SURVEY)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.PLANNED)).isEqualTo(1)
            assertThat(state.rows.map { it.instanceId }).containsExactly("i-overdue")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row carries the tag, deck short code, localised title and signed day delta`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.selectSegment(DueSegment.THIS_WEEK)
            val state = awaitUntil { it.segment == DueSegment.THIS_WEEK }
            val row = state.rows.single()
            assertThat(row.tag).isEqualTo("FE-UD-01")
            assertThat(row.deckShortName).isEqualTo("UD")
            assertThat(row.taskTitle.resolve(turkish = false))
                .isEqualTo("Portable fire extinguisher — monthly check")
            assertThat(row.taskTitle.resolve(turkish = true))
                .isEqualTo("Portatif yangın söndürücü — aylık kontrol")
            assertThat(row.dayDelta).isEqualTo(3)
            assertThat(row.performedBy).isEqualTo(PerformedBy.SHIP_STAFF)
            assertThat(row.group).isEqualTo(EquipmentGroup.FFE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters combine across dimensions`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.selectSegment(DueSegment.THIS_MONTH)
            awaitUntil { it.segment == DueSegment.THIS_MONTH }

            model.setGroupFilter(EquipmentGroup.LSA)
            var state = awaitUntil { it.filters.group == EquipmentGroup.LSA }
            assertThat(state.rows.map { it.instanceId }).containsExactly("i-month")

            // Adding a second dimension that contradicts the first empties the list.
            model.setPerformedByFilter(PerformedBy.SHIP_STAFF)
            state = awaitUntil { it.filters.performedBy == PerformedBy.SHIP_STAFF }
            assertThat(state.rows).isEmpty()
            assertThat(state.countOf(DueSegment.THIS_MONTH)).isEqualTo(0)

            model.clearFilters()
            state = awaitUntil { !it.filters.isActive }
            assertThat(state.rows.map { it.instanceId }).containsExactly("i-month")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the deck filter narrows the segment counts`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.setDeckFilter("deck-upper")
            val state = awaitUntil { it.filters.deckId == "deck-upper" }
            assertThat(state.countOf(DueSegment.OVERDUE)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.THIS_MONTH)).isEqualTo(0)
            assertThat(state.countOf(DueSegment.BEFORE_SURVEY)).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the category filter uses the equipment cross-reference`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.setCategoryFilter(CATEGORY)
            val state = awaitUntil { it.filters.categoryId == CATEGORY && it.counts.values.sum() == 3 }
            // Only the extinguisher carries the category, so its three occurrences survive.
            assertThat(state.countOf(DueSegment.OVERDUE)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.THIS_WEEK)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.PLANNED)).isEqualTo(1)
            assertThat(state.countOf(DueSegment.THIS_MONTH)).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `survey prep groups by performer and summarises the shore list`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.setSurveyPrep(true)
            val state = awaitUntil { it.surveyPrep != null }
            val prep = checkNotNull(state.surveyPrep)

            assertThat(prep.certExpiry).isEqualTo(certExpiry)
            assertThat(prep.daysToExpiry).isEqualTo(100)
            // Overdue and this-week extinguisher work is the ship's own; the two lifeboat exams
            // need an authorised provider. The occurrence past the certificate is out of scope.
            assertThat(prep.shipStaff.map { it.instanceId }).containsExactly("i-overdue", "i-week").inOrder()
            assertThat(prep.shoreProvider.map { it.instanceId }).containsExactly("i-month", "i-survey").inOrder()
            assertThat(prep.shoppingList).hasSize(1)
            assertThat(prep.shoppingList.single().count).isEqualTo(2)
            assertThat(prep.shoppingList.single().performedBy)
                .isEqualTo(PerformedBy.AUTHORISED_SERVICE_PROVIDER)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the export payload mirrors the visible segment and its filters`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.selectSegment(DueSegment.THIS_WEEK)
            model.setDeckFilter("deck-upper")
            awaitUntil { it.segment == DueSegment.THIS_WEEK && it.filters.deckId == "deck-upper" }

            val request = model.buildExportRequest()
            assertThat(request.vesselName).isEqualTo("MV Example")
            assertThat(request.vesselImoNumber).isEqualTo("9074729")
            assertThat(request.segment).isEqualTo(DueSegment.THIS_WEEK)
            assertThat(request.generatedOnEpochDay).isEqualTo(today)
            assertThat(request.filters.deckName).isEqualTo("UD")
            assertThat(request.lines.map { it.tag }).containsExactly("FE-UD-01")
            assertThat(request.lines.single().dayDelta).isEqualTo(3)
            assertThat(request.lines.single().performedBy).isEqualTo(PerformedBy.SHIP_STAFF)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing a task writes the evidence, the grade and asks for a recomputation`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.completeTask(
                TaskCompletionInput(
                    instanceId = "i-week",
                    equipmentId = FE,
                    completedDate = today,
                    completedBy = "3/O",
                    serviceProvider = "  ",
                    certificateNumber = "CERT-42",
                    findings = "Gauge in the green band",
                    conditionAfter = ConditionGrade.ACCEPTABLE,
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        val completion = maintenance.completions.single()
        assertThat(completion.instanceId).isEqualTo("i-week")
        assertThat(completion.completedBy).isEqualTo("3/O")
        // Blank optional fields are normalised away rather than stored as whitespace.
        assertThat(completion.serviceProvider).isNull()
        assertThat(completion.certificateNumber).isEqualTo("CERT-42")
        assertThat(completion.conditionAfter).isEqualTo(ConditionGrade.ACCEPTABLE)

        assertThat(maintenanceFake.instances.value.getValue("i-week").status).isEqualTo(TaskStatus.DONE)
        assertThat(equipment.equipment.value.getValue(FE).condition).isEqualTo(ConditionGrade.ACCEPTABLE)
        assertThat(maintenance.recomputedEquipmentIds).contains(FE)
    }

    @Test
    fun `deferring writes SKIPPED with the reason and keeps the occurrence on the list`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitLoaded()
            model.deferTask("i-overdue", "Spare on order, ETA Rotterdam")
            val state = awaitUntil { it.countOf(DueSegment.OVERDUE) == 0 }

            val deferred = maintenanceFake.instances.value.getValue("i-overdue")
            assertThat(deferred.status).isEqualTo(TaskStatus.SKIPPED)
            assertThat(deferred.findings).isEqualTo("Spare on order, ETA Rotterdam")
            assertThat(deferred.dueDate).isEqualTo(today - 40)
            // It moves to Planned rather than disappearing.
            assertThat(state.countOf(DueSegment.PLANNED)).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
        // A deferral is an annotation, not a completion: the schedule is untouched.
        assertThat(maintenance.recomputedEquipmentIds).isEmpty()
        assertThat(maintenance.completions).isEmpty()
    }

    @Test
    fun `with no active vessel the list is empty and says so`() = runTest {
        vessels.vessels.value = emptyMap()
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertThat(state.hasVessel).isFalse()
            assertThat(state.rows).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<DueUiState>.awaitLoaded(): DueUiState =
        awaitUntil { !it.loading }

    private suspend fun ReceiveTurbine<DueUiState>.awaitUntil(
        predicate: (DueUiState) -> Boolean,
    ): DueUiState {
        var state = awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }

    private companion object {
        const val VESSEL = "vessel-under-test"
        const val FE = "equipment-extinguisher"
        const val LB = "equipment-lifeboat"
        const val CATEGORY = "category-weekly"
        const val EXTINGUISHER = "FFE_PORTABLE_EXTINGUISHER"
        const val LIFEBOAT = "LSA_LIFEBOAT_ENCLOSED"
        const val MONTHLY_TASK = "FE_MONTHLY_INSPECTION"
        const val ANNUAL_TASK = "LB_ANNUAL_THOROUGH_EXAM"
    }
}
