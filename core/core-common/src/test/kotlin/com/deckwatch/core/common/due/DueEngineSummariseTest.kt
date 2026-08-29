package com.deckwatch.core.common.due

import com.deckwatch.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** §11.1 (5) — denormalising the soonest open occurrence onto the equipment row. */
class DueEngineSummariseTest {

    private val engine = DueEngine { day(2026, 1, 20) }

    @Test
    fun `summarise picks the soonest open occurrence`() {
        val summary = engine.summarise(
            listOf(
                taskInstance("a", "LATE_TASK", day(2026, 9, 1)),
                taskInstance("b", "SOON_TASK", day(2026, 2, 1)),
                taskInstance("c", "MID_TASK", day(2026, 5, 1)),
            ),
        )
        assertThat(summary.nextDueDate).isEqualTo(day(2026, 2, 1))
        assertThat(summary.nextDueTaskKey).isEqualTo("SOON_TASK")
    }

    @Test
    fun `summarise skips DONE instances even when they are the soonest`() {
        val summary = engine.summarise(
            listOf(
                taskInstance("a", "DONE_TASK", day(2026, 1, 1), TaskStatus.DONE, completedDate = day(2026, 1, 1)),
                taskInstance("b", "OPEN_TASK", day(2026, 5, 1)),
            ),
        )
        assertThat(summary.nextDueTaskKey).isEqualTo("OPEN_TASK")
        assertThat(summary.nextDueDate).isEqualTo(day(2026, 5, 1))
    }

    @Test
    fun `summarise skips NOT_APPLICABLE instances`() {
        val summary = engine.summarise(
            listOf(
                taskInstance("a", "NA_TASK", day(2026, 1, 1), TaskStatus.NOT_APPLICABLE),
                taskInstance("b", "OPEN_TASK", day(2026, 5, 1)),
            ),
        )
        assertThat(summary.nextDueTaskKey).isEqualTo("OPEN_TASK")
    }

    @Test
    fun `summarise counts OVERDUE DUE_SOON PENDING and SKIPPED as still owed`() {
        listOf(TaskStatus.OVERDUE, TaskStatus.DUE_SOON, TaskStatus.PENDING, TaskStatus.SKIPPED)
            .forEach { status ->
                val summary = engine.summarise(
                    listOf(taskInstance("a", "T", day(2026, 3, 3), status)),
                )
                assertThat(summary.nextDueTaskKey).isEqualTo("T")
            }
    }

    @Test
    fun `summarise returns nulls when nothing is open`() {
        val summary = engine.summarise(
            listOf(
                taskInstance("a", "DONE_TASK", day(2026, 1, 1), TaskStatus.DONE),
                taskInstance("b", "NA_TASK", day(2026, 2, 1), TaskStatus.NOT_APPLICABLE),
            ),
        )
        assertThat(summary.nextDueDate).isNull()
        assertThat(summary.nextDueTaskKey).isNull()
    }

    @Test
    fun `summarise of an empty list is empty`() {
        assertThat(engine.summarise(emptyList())).isEqualTo(DueSummary())
    }

    @Test
    fun `ties on the due date are broken by task key so the value is stable`() {
        val sameDay = day(2026, 4, 4)
        val forwards = engine.summarise(
            listOf(taskInstance("a", "ZULU", sameDay), taskInstance("b", "ALPHA", sameDay)),
        )
        val backwards = engine.summarise(
            listOf(taskInstance("b", "ALPHA", sameDay), taskInstance("a", "ZULU", sameDay)),
        )
        assertThat(forwards.nextDueTaskKey).isEqualTo("ALPHA")
        assertThat(backwards).isEqualTo(forwards)
    }

    @Test
    fun `EngineResult applies the summary onto the equipment row`() {
        val result = EngineResult(emptyList(), day(2026, 5, 1), "SOME_TASK")
        val updated = result.applyTo(equipment(), updatedAtMillis = 1234L)
        assertThat(updated.nextDueDate).isEqualTo(day(2026, 5, 1))
        assertThat(updated.nextDueTaskKey).isEqualTo("SOME_TASK")
        assertThat(updated.updatedAt).isEqualTo(1234L)
    }

    @Test
    fun `EngineResult applyTo leaves updatedAt alone by default`() {
        val original = equipment().copy(updatedAt = 99L)
        assertThat(EngineResult(emptyList()).applyTo(original).updatedAt).isEqualTo(99L)
    }

    @Test
    fun `EngineResult exposes its summary`() {
        val result = EngineResult(emptyList(), 10L, "T")
        assertThat(result.summary).isEqualTo(DueSummary(10L, "T"))
    }
}
