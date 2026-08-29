package com.deckwatch.feature.equipment

import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.AttributeKind
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The monthly-checklist rule of §9.3 — which task a completed sweep closes. */
class MonthlyChecklistTest {

    @Test
    fun `the single monthly key wins`() {
        val type = TestData.equipmentType(taskKeys = listOf("FE_MONTHLY_INSPECTION", "FE_ANNUAL_SERVICE"))
        assertThat(MonthlyChecklist.taskKeyFor(type)).isEqualTo("FE_MONTHLY_INSPECTION")
    }

    @Test
    fun `a checklist task beats another monthly task on the same type`() {
        // The seeded survival craft carry both; only one of them is a tick-box sweep.
        val type = TestData.equipmentType(
            taskKeys = listOf(
                "SC_WEEKLY_VISUAL_INSPECTION",
                "LB_MONTHLY_MOVE_FROM_STOWED",
                "LSA_MONTHLY_CHECKLIST_INSPECTION",
                "SC_ANNUAL_THOROUGH_EXAM",
            ),
        )
        assertThat(MonthlyChecklist.taskKeyFor(type)).isEqualTo("LSA_MONTHLY_CHECKLIST_INSPECTION")
    }

    @Test
    fun `with several plain monthly keys the catalogue order decides`() {
        val type = TestData.equipmentType(
            taskKeys = listOf("FD_MONTHLY_SAMPLE_TEST", "SCBA_MONTHLY_OFFICER_CHECK"),
        )
        assertThat(MonthlyChecklist.taskKeyFor(type)).isEqualTo("FD_MONTHLY_SAMPLE_TEST")
    }

    @Test
    fun `a type with no monthly task offers no completion`() {
        val type = TestData.equipmentType(taskKeys = listOf("FE_ANNUAL_SERVICE", "FE_TEN_YEARLY_HYDROSTATIC"))
        assertThat(MonthlyChecklist.taskKeyFor(type)).isNull()
    }

    @Test
    fun `checklist items are the schema's monthlyChecklist booleans, in order`() {
        val type = TestData.equipmentType(
            attributeSchema = listOf(
                TestData.attributeDefinition(),
                boolean("accessUnobstructed", checklist = true),
                boolean("sealIntact", checklist = true),
                boolean("spareCharge", checklist = false),
            ),
        )
        assertThat(MonthlyChecklist.items(type).map { it.key })
            .containsExactly("accessUnobstructed", "sealIntact")
            .inOrder()
    }

    @Test
    fun `all ticked only when every box is true`() {
        val items = listOf(boolean("a", checklist = true), boolean("b", checklist = true))
        assertThat(MonthlyChecklist.allTicked(items, mapOf("a" to "true", "b" to "false"))).isFalse()
        assertThat(MonthlyChecklist.tickedCount(items, mapOf("a" to "true", "b" to "false"))).isEqualTo(1)
        assertThat(MonthlyChecklist.allTicked(items, mapOf("a" to "true", "b" to "true"))).isTrue()
        assertThat(MonthlyChecklist.allTicked(emptyList(), emptyMap())).isFalse()
    }

    @Test
    fun `the earliest still-open occurrence is the one closed`() {
        val done = TestData.taskInstance(
            id = "done",
            taskKey = "FE_MONTHLY_INSPECTION",
            dueDate = TestData.day(2025, 12, 1),
            status = TaskStatus.DONE,
        )
        val overdue = TestData.taskInstance(
            id = "overdue",
            taskKey = "FE_MONTHLY_INSPECTION",
            dueDate = TestData.day(2026, 1, 1),
            status = TaskStatus.OVERDUE,
        )
        val next = TestData.taskInstance(
            id = "next",
            taskKey = "FE_MONTHLY_INSPECTION",
            dueDate = TestData.day(2026, 2, 1),
            status = TaskStatus.PENDING,
        )
        val other = TestData.taskInstance(id = "other", taskKey = "FE_ANNUAL_SERVICE")

        val picked = MonthlyChecklist.openInstanceFor(listOf(next, done, overdue, other), "FE_MONTHLY_INSPECTION")
        assertThat(picked?.id).isEqualTo("overdue")
    }

    @Test
    fun `a key with nothing open closes nothing`() {
        val done = TestData.taskInstance(id = "done", status = TaskStatus.DONE)
        assertThat(MonthlyChecklist.openInstanceFor(listOf(done), "FE_MONTHLY_INSPECTION")).isNull()
    }

    private fun boolean(key: String, checklist: Boolean) = AttributeDefinition(
        key = key,
        kind = AttributeKind.BOOLEAN,
        labelEn = key,
        monthlyChecklist = checklist,
    )
}
