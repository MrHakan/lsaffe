package com.deckwatch.feature.notes

import app.cash.turbine.test
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.testing.FakeMaintenanceRepository
import com.deckwatch.core.testing.FakeReferenceRepository
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Assembly of the equipment-type × interval × performed-by matrix — §8.3. */
class IntervalMatrixViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reference = FakeReferenceRepository()
    private val maintenance = FakeMaintenanceRepository(reference = reference)

    private val extinguisherType = TestData.equipmentType(
        typeKey = "FFE_PORTABLE_EXTINGUISHER",
        nameEn = "Portable fire extinguisher",
        taskKeys = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
    )
    private val lifeboatType = TestData.equipmentType(
        typeKey = "LSA_LIFEBOAT_TEC",
        nameEn = "Lifeboat (totally enclosed)",
        taskKeys = emptyList(),
    )

    /** Linked to its type only from the catalogue side, via `EquipmentType.taskKeys`. */
    private val monthlyCheck = TestData.taskDefinition(
        key = "FE_MONTHLY_INSPECTION",
        appliesToTypeKeys = emptyList(),
        titleEn = "Portable fire extinguisher — monthly check",
        intervalKind = IntervalKind.MONTHLY,
        performedBy = PerformedBy.SHIP_STAFF,
        regulationRefs = listOf("MSC1_CIRC1432_EXTINGUISHERS"),
    )
    private val annualService = TestData.taskDefinition(
        key = "FE_ANNUAL_SERVICE",
        appliesToTypeKeys = listOf("FFE_PORTABLE_EXTINGUISHER"),
        titleEn = "Portable fire extinguisher — annual service",
        intervalKind = IntervalKind.ANNUAL,
        performedBy = PerformedBy.SHORE_FACILITY,
        regulationRefs = listOf("MSC1_CIRC1432_EXTINGUISHERS"),
        flagOverrides = mapOf("LIB" to "Trained ship's crew may service (STCW A-VI/3)."),
    )
    private val boatOverloadTest = TestData.taskDefinition(
        key = "LB_FIVE_YEARLY_OVERLOAD",
        appliesToTypeKeys = listOf("LSA_LIFEBOAT_TEC"),
        titleEn = "On-load release gear — 5-yearly overload operational test",
        intervalKind = IntervalKind.FIVE_YEARLY,
        performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
        regulationRefs = listOf("MSC_402_96"),
        flagOverrides = mapOf("RMI" to "RO surveyor must attend."),
    )
    private val orphanTask = TestData.taskDefinition(
        key = "GEA_WEEKLY_TEST",
        appliesToTypeKeys = listOf("LSA_GENERAL_EMERGENCY_ALARM"),
        titleEn = "General emergency alarm test",
        intervalKind = IntervalKind.WEEKLY,
        performedBy = PerformedBy.SHIP_STAFF,
        regulationRefs = emptyList(),
    )

    private val cards = listOf(
        TestData.regulationCard(
            refKey = "MSC1_CIRC1432_EXTINGUISHERS",
            section = RegulationSection.FFE,
            citation = "MSC.1/Circ.1432",
        ),
        TestData.regulationCard(
            refKey = "MSC_402_96",
            section = RegulationSection.LSA,
            citation = "MSC.402(96)",
        ),
    )

    @Before
    fun seed() {
        reference.seedEquipmentType(extinguisherType)
        reference.seedEquipmentType(lifeboatType)
        cards.forEach(reference::seedRegulationCard)
        maintenance.definitions.value =
            listOf(monthlyCheck, annualService, boatOverloadTest, orphanTask).associateBy { it.key }
    }

    // ------------------------------------------------------------------ pure assembly

    @Test
    fun `a definition is claimed by a type from either side of the link`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType, lifeboatType),
            definitions = listOf(monthlyCheck, annualService, boatOverloadTest),
            cards = cards,
        )

        val extinguisher = groups.single { it.typeKey == "FFE_PORTABLE_EXTINGUISHER" }
        // FE_MONTHLY_INSPECTION comes from the catalogue's taskKeys, FE_ANNUAL_SERVICE from
        // the definition's appliesToTypeKeys.
        assertThat(extinguisher.rows.map { it.taskKey })
            .containsExactly("FE_ANNUAL_SERVICE", "FE_MONTHLY_INSPECTION")
    }

    @Test
    fun `groups are ordered by equipment type name and rows by task title`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType, lifeboatType),
            definitions = listOf(monthlyCheck, annualService, boatOverloadTest),
            cards = cards,
        )

        assertThat(groups.map { it.typeName })
            .containsExactly("Lifeboat (totally enclosed)", "Portable fire extinguisher")
            .inOrder()
        assertThat(groups.first { it.typeKey == "FFE_PORTABLE_EXTINGUISHER" }.rows.map { it.title })
            .containsExactly(
                "Portable fire extinguisher — annual service",
                "Portable fire extinguisher — monthly check",
            )
            .inOrder()
    }

    @Test
    fun `interval, performed-by and the linked card are carried onto the row`() {
        val groups = buildIntervalMatrix(
            types = listOf(lifeboatType),
            definitions = listOf(boatOverloadTest),
            cards = cards,
        )

        val row = groups.single().rows.single()
        assertThat(row.intervalKind).isEqualTo(IntervalKind.FIVE_YEARLY)
        assertThat(row.performedBy).isEqualTo(PerformedBy.AUTHORISED_SERVICE_PROVIDER)
        assertThat(row.cardRefKey).isEqualTo("MSC_402_96")
        assertThat(row.cardCitation).isEqualTo("MSC.402(96)")
    }

    @Test
    fun `flagOverrides raise the divergence marker and list the diverging flags`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType),
            definitions = listOf(monthlyCheck, annualService),
            cards = cards,
        )
        val rows = groups.single().rows.associateBy { it.taskKey }

        assertThat(rows.getValue("FE_ANNUAL_SERVICE").hasFlagDivergence).isTrue()
        assertThat(rows.getValue("FE_ANNUAL_SERVICE").divergentFlags).containsExactly("LIB")
        assertThat(rows.getValue("FE_MONTHLY_INSPECTION").hasFlagDivergence).isFalse()
    }

    @Test
    fun `a regulationRef with no bundled card leaves the row unlinked rather than dangling`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType),
            definitions = listOf(annualService),
            cards = emptyList(),
        )

        val row = groups.single().rows.single()
        assertThat(row.cardRefKey).isNull()
        assertThat(row.cardCitation).isNull()
    }

    @Test
    fun `a definition matching no catalogue type lands in the trailing catch-all group`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType, lifeboatType),
            definitions = listOf(annualService, boatOverloadTest, orphanTask),
            cards = cards,
        )

        val trailing = groups.last()
        assertThat(trailing.typeKey).isNull()
        assertThat(trailing.rows.map { it.taskKey }).containsExactly("GEA_WEEKLY_TEST")
    }

    @Test
    fun `filtering on a type name keeps every row of that type`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType, lifeboatType),
            definitions = listOf(monthlyCheck, annualService, boatOverloadTest),
            cards = cards,
            query = "extinguisher",
        )

        assertThat(groups).hasSize(1)
        assertThat(groups.single().typeKey).isEqualTo("FFE_PORTABLE_EXTINGUISHER")
        assertThat(groups.single().rows).hasSize(2)
    }

    @Test
    fun `filtering on a task title narrows within the group`() {
        val groups = buildIntervalMatrix(
            types = listOf(extinguisherType, lifeboatType),
            definitions = listOf(monthlyCheck, annualService, boatOverloadTest),
            cards = cards,
            query = "overload",
        )

        assertThat(groups).hasSize(1)
        assertThat(groups.single().rows.map { it.taskKey }).containsExactly("LB_FIVE_YEARLY_OVERLOAD")
    }

    // ------------------------------------------------------------------ through the ViewModel

    @Test
    fun `the ViewModel assembles the matrix from the catalogue, the definitions and the cards`() = runTest {
        val viewModel = IntervalMatrixViewModel(reference, maintenance)

        viewModel.uiState.test {
            val state = awaitState { it.rowCount == 4 }

            assertThat(state.groups.map { it.typeName })
                .containsExactly("Lifeboat (totally enclosed)", "Portable fire extinguisher", "")
                .inOrder()
            assertThat(state.groups.last().typeKey).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the ViewModel filter narrows the matrix`() = runTest {
        val viewModel = IntervalMatrixViewModel(reference, maintenance)

        viewModel.uiState.test {
            awaitState { it.rowCount == 4 }
            viewModel.onQueryChange("lifeboat")

            val state = awaitState { it.query == "lifeboat" }
            assertThat(state.groups).hasSize(1)
            assertThat(state.groups.single().rows.map { it.taskKey })
                .containsExactly("LB_FIVE_YEARLY_OVERLOAD")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no task definitions means an empty matrix, not a crash`() = runTest {
        maintenance.definitions.value = emptyMap()
        val viewModel = IntervalMatrixViewModel(reference, maintenance)

        viewModel.uiState.test {
            val state = awaitState { it.groups.isEmpty() }
            assertThat(state.isEmpty).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
