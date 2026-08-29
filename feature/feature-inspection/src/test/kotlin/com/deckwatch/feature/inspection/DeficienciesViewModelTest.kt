package com.deckwatch.feature.inspection

import com.deckwatch.core.model.DeficiencyStatus
import com.deckwatch.core.model.Severity
import com.deckwatch.core.testing.FakeEquipmentRepository
import com.deckwatch.core.testing.FakeInspectionRepository
import com.deckwatch.core.testing.FakeVesselRepository
import com.deckwatch.core.testing.SequentialIds
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Open and closed deficiencies, severity-first — §6.8. */
class DeficienciesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = TestData.referenceDay

    private val vessels = FakeVesselRepository()
    private val equipment = FakeEquipmentRepository()
    private val inspection = FakeInspectionRepository()

    private fun viewModel() = DeficienciesViewModel(
        vesselRepository = vessels,
        equipmentRepository = equipment,
        inspectionRepository = inspection,
        today = { today },
        idFactory = SequentialIds("deficiency"),
    )

    @Before
    fun seed() {
        val vessel = TestData.vessel(id = VESSEL, isActive = true)
        vessels.vessels.value = mapOf(vessel.id to vessel)
        val gear = TestData.equipment(id = "e-1", vesselId = VESSEL, tag = "FE-UD-01")
        equipment.equipment.value = mapOf(gear.id to gear)
    }

    @Test
    fun `open and closed are split and the worst severity leads`() = runTest {
        inspection.deficiencies.value = listOf(
            TestData.deficiency(
                id = "d-observation",
                vesselId = VESSEL,
                severity = Severity.OBSERVATION,
                status = DeficiencyStatus.OPEN,
            ),
            TestData.deficiency(
                id = "d-critical",
                vesselId = VESSEL,
                severity = Severity.CRITICAL_DETAINABLE,
                status = DeficiencyStatus.IN_PROGRESS,
            ),
            TestData.deficiency(
                id = "d-major",
                vesselId = VESSEL,
                severity = Severity.MAJOR,
                status = DeficiencyStatus.DEFERRED_TO_OFFICE,
            ),
            TestData.deficiency(
                id = "d-closed",
                vesselId = VESSEL,
                severity = Severity.MINOR,
                status = DeficiencyStatus.CLOSED,
            ),
        ).associateBy { it.id }

        val state = viewModel().uiState.first { !it.loading }

        // DEFERRED_TO_OFFICE is still open work — only CLOSED leaves the open tab (§6.8).
        assertThat(state.open.map { it.deficiency.id })
            .containsExactly("d-critical", "d-major", "d-observation").inOrder()
        assertThat(state.closed.map { it.deficiency.id }).containsExactly("d-closed")
        assertThat(state.equipmentOptions.map { it.label }).containsExactly("FE-UD-01")
    }

    @Test
    fun `a deficiency shows the tag of the equipment it was raised against`() = runTest {
        val deficiency = TestData.deficiency(id = "d-1", vesselId = VESSEL, equipmentId = "e-1")
        inspection.deficiencies.value = mapOf(deficiency.id to deficiency)

        val state = viewModel().uiState.first { !it.loading }
        assertThat(state.open.single().equipmentTag).isEqualTo("FE-UD-01")
        assertThat(state.open.single().symbolKey).isEqualTo("FES001")
    }

    @Test
    fun `raising writes a dated, open deficiency against the active vessel`() = runTest {
        val model = viewModel()
        model.uiState.first { !it.loading }

        model.raise(
            DeficiencyDraft(
                equipmentId = "e-1",
                severity = Severity.MAJOR,
                title = "Pressure gauge outside green band",
                description = "Gauge reads below the green band.",
                correctiveAction = "  ",
                targetDate = today + 14,
                raisedBy = "3/O",
            ),
        )

        val raised = inspection.deficiencies.value.values.single()
        assertThat(raised.id).isEqualTo("deficiency-1")
        assertThat(raised.vesselId).isEqualTo(VESSEL)
        assertThat(raised.equipmentId).isEqualTo("e-1")
        assertThat(raised.raisedDate).isEqualTo(today)
        assertThat(raised.severity).isEqualTo(Severity.MAJOR)
        assertThat(raised.status).isEqualTo(DeficiencyStatus.OPEN)
        assertThat(raised.targetDate).isEqualTo(today + 14)
        // Blank optional text is normalised away rather than stored as whitespace.
        assertThat(raised.correctiveAction).isNull()
    }

    @Test
    fun `a titleless draft is refused`() = runTest {
        val model = viewModel()
        model.uiState.first { !it.loading }
        model.raise(DeficiencyDraft(title = "   "))
        assertThat(inspection.deficiencies.value).isEmpty()
    }

    @Test
    fun `editing records the corrective action, target date and status`() = runTest {
        val existing = TestData.deficiency(id = "d-1", vesselId = VESSEL, status = DeficiencyStatus.OPEN)
        inspection.deficiencies.value = mapOf(existing.id to existing)
        val model = viewModel()
        model.uiState.first { !it.loading }

        model.update(
            existing,
            existing.toDraft().copy(
                correctiveAction = "Landed for service in Rotterdam",
                targetDate = today + 30,
                status = DeficiencyStatus.IN_PROGRESS,
                sparePartRequired = "Gauge assembly",
            ),
        )

        val updated = inspection.deficiencies.value.getValue("d-1")
        assertThat(updated.correctiveAction).isEqualTo("Landed for service in Rotterdam")
        assertThat(updated.targetDate).isEqualTo(today + 30)
        assertThat(updated.status).isEqualTo(DeficiencyStatus.IN_PROGRESS)
        assertThat(updated.sparePartRequired).isEqualTo("Gauge assembly")
        assertThat(updated.raisedDate).isEqualTo(existing.raisedDate)
    }

    @Test
    fun `closing stamps who closed it and when, and moves it to the closed tab`() = runTest {
        val existing = TestData.deficiency(id = "d-1", vesselId = VESSEL, status = DeficiencyStatus.OPEN)
        inspection.deficiencies.value = mapOf(existing.id to existing)
        val model = viewModel()
        model.uiState.first { !it.loading }

        model.close(existing, closedBy = "C/O")

        val closed = inspection.deficiencies.value.getValue("d-1")
        assertThat(closed.status).isEqualTo(DeficiencyStatus.CLOSED)
        assertThat(closed.closedBy).isEqualTo("C/O")
        assertThat(closed.closedDate).isEqualTo(today)

        val state = model.uiState.first { !it.loading }
        assertThat(state.open).isEmpty()
        assertThat(state.closed.map { it.deficiency.id }).containsExactly("d-1")
    }

    @Test
    fun `nothing is written without an active vessel`() = runTest {
        vessels.vessels.value = emptyMap()
        val model = viewModel()
        val state = model.uiState.first { !it.loading }
        assertThat(state.hasVessel).isFalse()

        model.raise(DeficiencyDraft(title = "Orphan finding"))
        assertThat(inspection.deficiencies.value).isEmpty()
    }

    private companion object {
        const val VESSEL = "vessel-under-test"
    }
}
