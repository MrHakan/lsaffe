package com.deckwatch.feature.equipment

import com.deckwatch.core.common.due.DueEngine
import com.deckwatch.core.common.due.VesselDueContext
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The live due-date preview of §7.5.4 and the attribute -> task inference it rests on. */
class DuePreviewTest {

    private val today = TestData.day(2026, 1, 15)
    private val engine = DueEngine { today }

    private val definitions = listOf(
        TestData.taskDefinition(key = "FE_MONTHLY_INSPECTION", intervalKind = IntervalKind.MONTHLY),
        TestData.taskDefinition(
            key = "FE_ANNUAL_SERVICE",
            intervalKind = IntervalKind.ANNUAL,
            titleEn = "Portable fire extinguisher — annual service",
        ),
        TestData.taskDefinition(
            key = "FE_CO2_CYLINDER_WEIGHT_CHECK",
            intervalKind = IntervalKind.EVENT_DRIVEN,
            titleEn = "CO2 extinguisher — cylinder weight check",
        ),
    ).associateBy { it.key }

    private val type = TestData.equipmentType(
        taskKeys = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
        attributeSchema = listOf(TestData.attributeDefinition()),
    )

    @Test
    fun `the preview schedules from the installed date, soonest first`() {
        val rows = DuePreview.compute(
            type = type,
            definitions = definitions,
            attributesJson = "{}",
            installedDate = TestData.day(2026, 1, 1),
            manufactureDate = TestData.day(2020, 6, 1),
            vessel = VesselDueContext(),
            todayEpochDay = today,
            engine = engine,
        )
        assertThat(rows.map { it.taskKey })
            .containsExactly("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE")
            .inOrder()
        assertThat(rows[0].dueDate).isEqualTo(TestData.day(2026, 2, 1))
        assertThat(rows[1].dueDate).isEqualTo(TestData.day(2027, 1, 1))
    }

    @Test
    fun `an affectsTasks value adds its task to the preview, undated tasks last`() {
        val rows = DuePreview.compute(
            type = type,
            definitions = definitions,
            attributesJson = """{"extinguishingMedium":"CO2"}""",
            installedDate = TestData.day(2026, 1, 1),
            manufactureDate = null,
            vessel = VesselDueContext(),
            todayEpochDay = today,
            engine = engine,
        )
        assertThat(rows.map { it.taskKey }).contains("FE_CO2_CYLINDER_WEIGHT_CHECK")
        assertThat(rows.last().taskKey).isEqualTo("FE_CO2_CYLINDER_WEIGHT_CHECK")
        assertThat(rows.last().dueDate).isNull()
    }

    @Test
    fun `a task key with no definition is skipped rather than throwing`() {
        val rows = DuePreview.compute(
            type = TestData.equipmentType(taskKeys = listOf("NOT_IN_THE_SEED")),
            definitions = definitions,
            attributesJson = "{}",
            installedDate = null,
            manufactureDate = null,
            vessel = VesselDueContext(),
            todayEpochDay = today,
            engine = engine,
        )
        assertThat(rows).isEmpty()
    }

    @Test
    fun `tasksAddedBy names only the tasks the value brings in`() {
        val attribute = TestData.attributeDefinition()
        assertThat(DuePreview.tasksAddedBy(type, attribute, "CO2"))
            .containsExactly("FE_CO2_CYLINDER_WEIGHT_CHECK")
        assertThat(DuePreview.tasksAddedBy(type, attribute, "")).isEmpty()
        assertThat(DuePreview.tasksAddedBy(type, attribute, "WATER")).isEmpty()
    }

    @Test
    fun `a dated attribute previews the next occurrence of the task it names`() {
        val attribute = AttributeDefinition(
            key = "lastAnnualServiceDate",
            kind = AttributeKind.DATE,
            labelEn = "Last annual service",
        )
        val row = DuePreview.anchorPreview(
            attribute = attribute,
            enteredEpochDay = TestData.day(2025, 6, 1),
            derivedTaskKeys = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"),
            definitions = definitions,
            vessel = VesselDueContext(),
            todayEpochDay = today,
            engine = engine,
        )
        assertThat(row?.taskKey).isEqualTo("FE_ANNUAL_SERVICE")
        assertThat(row?.dueDate).isEqualTo(TestData.day(2026, 6, 1))
    }

    @Test
    fun `a date the model cannot link to one task previews nothing`() {
        val attribute = AttributeDefinition(
            key = "someOtherDate",
            kind = AttributeKind.DATE,
            labelEn = "Some other date",
        )
        val row = DuePreview.anchorPreview(
            attribute = attribute,
            enteredEpochDay = TestData.day(2025, 6, 1),
            derivedTaskKeys = definitions.keys,
            definitions = definitions,
            vessel = VesselDueContext(),
            todayEpochDay = today,
            engine = engine,
        )
        assertThat(row).isNull()
    }

    @Test
    fun `only DATE attributes with a value produce an anchor line`() {
        val notADate = AttributeDefinition(key = "remark", kind = AttributeKind.TEXT, labelEn = "Remark")
        assertThat(
            DuePreview.anchorPreview(notADate, TestData.day(2025, 6, 1), definitions.keys, definitions, VesselDueContext(), today, engine),
        ).isNull()

        val date = AttributeDefinition(key = "lastAnnualServiceDate", kind = AttributeKind.DATE, labelEn = "Last service")
        assertThat(
            DuePreview.anchorPreview(date, null, definitions.keys, definitions, VesselDueContext(), today, engine),
        ).isNull()
    }
}

/** The name-token inference that links a `DATE` attribute to its task — see [AttributeTaskLink]. */
class AttributeTaskLinkTest {

    private val taskKeys = listOf(
        "FE_MONTHLY_INSPECTION",
        "FE_ANNUAL_SERVICE",
        "FE_TEN_YEARLY_HYDROSTATIC",
        "RG_FIVE_YEARLY_OVERLOAD_TEST",
    )

    @Test
    fun `camel case attribute names split into scoring tokens`() {
        assertThat(AttributeTaskLink.attributeTokens("lastAnnualServiceDate"))
            .containsExactly("ANNUAL", "SERVICE")
        assertThat(AttributeTaskLink.taskTokens("FE_ANNUAL_SERVICE"))
            .containsExactly("FE", "ANNUAL", "SERVICE")
    }

    @Test
    fun `the seed's own names resolve to their task`() {
        assertThat(AttributeTaskLink.resolve("lastAnnualServiceDate", taskKeys)).isEqualTo("FE_ANNUAL_SERVICE")
        assertThat(AttributeTaskLink.resolve("lastHydrostaticTestDate", taskKeys)).isEqualTo("FE_TEN_YEARLY_HYDROSTATIC")
        assertThat(AttributeTaskLink.resolve("lastFiveYearlyOverloadTestDate", taskKeys))
            .isEqualTo("RG_FIVE_YEARLY_OVERLOAD_TEST")
    }

    @Test
    fun `an unrelated or ambiguous name resolves to nothing`() {
        assertThat(AttributeTaskLink.resolve("serviceProviderName", listOf("FE_ANNUAL_SERVICE", "SC_ANNUAL_SERVICE")))
            .isNull()
        assertThat(AttributeTaskLink.resolve("lastDate", taskKeys)).isNull()
        assertThat(AttributeTaskLink.resolve("capacityPersons", taskKeys)).isNull()
    }
}
