package com.deckwatch.core.common.due

import com.deckwatch.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** §11.1 (4), §11.4 — OVERDUE / DUE_SOON / PENDING and their exact boundaries. */
class DueEngineClassifyTest {

    private val due = day(2026, 6, 15)
    private val engine = DueEngine { due }

    private fun window(before: Int = 10, after: Int = 5) = DueComputation(
        dueDate = due,
        windowOpens = due - before,
        windowCloses = due + after,
    )

    // ---------------------------------------------------------------- OVERDUE boundary

    @Test
    fun `exactly on windowCloses the work is still in time`() {
        val computation = window()
        assertThat(engine.classify(computation, computation.windowCloses, leadTimeDays = 0))
            .isEqualTo(TaskStatus.DUE_SOON)
    }

    @Test
    fun `one day past windowCloses is overdue`() {
        val computation = window()
        assertThat(engine.classify(computation, computation.windowCloses + 1, leadTimeDays = 0))
            .isEqualTo(TaskStatus.OVERDUE)
    }

    @Test
    fun `far past windowCloses is overdue`() {
        val computation = window()
        assertThat(engine.classify(computation, computation.windowCloses + 400, leadTimeDays = 90))
            .isEqualTo(TaskStatus.OVERDUE)
    }

    @Test
    fun `a long lead time never masks an overdue item`() {
        val computation = window()
        assertThat(engine.classify(computation, computation.windowCloses + 1, leadTimeDays = 3650))
            .isEqualTo(TaskStatus.OVERDUE)
    }

    // ---------------------------------------------------------------- window-open boundary

    @Test
    fun `exactly on windowOpens the item is due soon`() {
        val computation = window()
        assertThat(engine.classify(computation, computation.windowOpens, leadTimeDays = 0))
            .isEqualTo(TaskStatus.DUE_SOON)
    }

    @Test
    fun `one day before windowOpens is pending when there is no lead time`() {
        val computation = window()
        assertThat(engine.classify(computation, computation.windowOpens - 1, leadTimeDays = 0))
            .isEqualTo(TaskStatus.PENDING)
    }

    @Test
    fun `on the due date itself the item is due soon`() {
        val computation = window()
        assertThat(engine.classify(computation, due, leadTimeDays = 0)).isEqualTo(TaskStatus.DUE_SOON)
    }

    // ---------------------------------------------------------------- lead-time boundary

    @Test
    fun `exactly lead-time days before the due date is due soon`() {
        val computation = window(before = 0, after = 0)
        assertThat(engine.classify(computation, due - DueEngine.DEFAULT_LEAD_TIME_DAYS))
            .isEqualTo(TaskStatus.DUE_SOON)
    }

    @Test
    fun `one day earlier than the lead time is pending`() {
        val computation = window(before = 0, after = 0)
        assertThat(engine.classify(computation, due - DueEngine.DEFAULT_LEAD_TIME_DAYS - 1))
            .isEqualTo(TaskStatus.PENDING)
    }

    @Test
    fun `the default lead time is 30 days`() {
        assertThat(DueEngine.DEFAULT_LEAD_TIME_DAYS).isEqualTo(30)
    }

    @Test
    fun `a wide tolerance window opens the item before the lead time does`() {
        val computation = window(before = 90, after = 90)
        assertThat(engine.classify(computation, due - 90, leadTimeDays = 30)).isEqualTo(TaskStatus.DUE_SOON)
        assertThat(engine.classify(computation, due - 91, leadTimeDays = 30)).isEqualTo(TaskStatus.PENDING)
    }

    @Test
    fun `a long lead time opens the item before the tolerance window does`() {
        val computation = window(before = 0, after = 0)
        assertThat(engine.classify(computation, due - 120, leadTimeDays = 120)).isEqualTo(TaskStatus.DUE_SOON)
        assertThat(engine.classify(computation, due - 121, leadTimeDays = 120)).isEqualTo(TaskStatus.PENDING)
    }

    @Test
    fun `a negative lead time is clamped to zero`() {
        val computation = window(before = 0, after = 0)
        assertThat(engine.classify(computation, due, leadTimeDays = -50)).isEqualTo(TaskStatus.DUE_SOON)
        assertThat(engine.classify(computation, due - 1, leadTimeDays = -50)).isEqualTo(TaskStatus.PENDING)
    }

    @Test
    fun `classify uses the injected clock when no date is given`() {
        val computation = window(before = 0, after = 0)
        assertThat(engine.classify(computation)).isEqualTo(TaskStatus.DUE_SOON)
    }

    @Test
    fun `classify never invents a recorded status`() {
        val computation = window()
        val statuses = (-200L..200L).map { engine.classify(computation, due + it) }.toSet()
        assertThat(statuses).containsNoneOf(TaskStatus.DONE, TaskStatus.SKIPPED, TaskStatus.NOT_APPLICABLE)
    }
}
