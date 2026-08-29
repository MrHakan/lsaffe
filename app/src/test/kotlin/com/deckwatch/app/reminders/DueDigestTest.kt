package com.deckwatch.app.reminders

import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

/** What the daily reminder counts, and when it stays quiet — §11.3. */
class DueDigestTest {

    @Test
    fun `nothing due means nothing is posted`() {
        val digest = DueDigest.from(listOf(instance(dueIn = 45)), TODAY)

        assertThat(digest.total).isEqualTo(0)
        assertThat(digest.worthNotifying).isFalse()
    }

    @Test
    fun `overdue and due-this-week are counted separately`() {
        val digest = DueDigest.from(
            listOf(
                instance(id = "a", dueIn = -5, windowClosesIn = -2),
                instance(id = "b", dueIn = 2),
                instance(id = "c", dueIn = 6),
            ),
            TODAY,
        )

        assertThat(digest.overdue).isEqualTo(1)
        assertThat(digest.thisWeek).isEqualTo(2)
        assertThat(digest.worthNotifying).isTrue()
    }

    @Test
    fun `work beyond the week is left to the Due tab`() {
        val digest = DueDigest.from(
            listOf(instance(id = "a", dueIn = 8), instance(id = "b", dueIn = 25)),
            TODAY,
        )

        assertThat(digest.total).isEqualTo(0)
    }

    @Test
    fun `a deferred job does not nag`() {
        val digest = DueDigest.from(
            listOf(instance(dueIn = -3, windowClosesIn = -1, status = TaskStatus.SKIPPED)),
            TODAY,
        )

        assertThat(digest.total).isEqualTo(0)
    }

    private fun instance(
        id: String = "instance-1",
        dueIn: Long,
        windowClosesIn: Long = dueIn + 3,
        status: TaskStatus = TaskStatus.PENDING,
    ) = TaskInstance(
        id = id,
        equipmentId = "equipment-1",
        taskKey = "FE_MONTHLY_INSPECTION",
        dueDate = TODAY + dueIn,
        windowOpens = TODAY + dueIn - 3,
        windowCloses = TODAY + windowClosesIn,
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )

    private companion object {
        const val TODAY = 20_000L
    }
}

/** The digest has to land on the configured hour, on the device's own clock — §11.3. */
class ReminderTimingTest {

    @Test
    fun `before the hour, it waits until later today`() {
        val delay = Reminders.delayUntilNext(8, 0, LocalDateTime.of(2026, 5, 1, 6, 30))

        assertThat(delay.toMinutes()).isEqualTo(90)
    }

    @Test
    fun `after the hour, it waits until tomorrow`() {
        val delay = Reminders.delayUntilNext(8, 0, LocalDateTime.of(2026, 5, 1, 9, 0))

        assertThat(delay.toHours()).isEqualTo(23)
    }

    @Test
    fun `exactly on the hour counts as today's slot having passed, never a double fire`() {
        val delay = Reminders.delayUntilNext(8, 0, LocalDateTime.of(2026, 5, 1, 8, 0))

        assertThat(delay.toHours()).isEqualTo(24)
    }
}
