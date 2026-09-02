package com.deckwatch.data.repository.work

import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The daily digest text and its counts — MASTER_PROMPT §11.3 — and the 03:00 schedule of §11.2.
 *
 * Robolectric only so that `androidx.work.Data` has a real Android runtime under it; nothing here
 * touches the database.
 */
@RunWith(RobolectricTestRunner::class)
class DueDigestTest {

    private val today = TestData.referenceDay

    @Test
    fun `counts overdue and due-this-week and ignores closed work`() {
        val instances = listOf(
            instance("a", due = today - 40, closes = today - 40, status = TaskStatus.OVERDUE),
            instance("b", due = today - 5, closes = today - 1, status = TaskStatus.DUE_SOON),
            instance("c", due = today + 2, closes = today + 2, status = TaskStatus.DUE_SOON),
            instance("d", due = today + 7, closes = today + 7, status = TaskStatus.PENDING),
            instance("e", due = today + 8, closes = today + 8, status = TaskStatus.PENDING),
            instance("f", due = today, closes = today, status = TaskStatus.DONE),
            instance("g", due = today, closes = today, status = TaskStatus.NOT_APPLICABLE),
        )

        val digest = computeDueDigest(instances, today)

        // "b" is past its window even though its stored status has not caught up yet.
        assertThat(digest.overdue).isEqualTo(2)
        // "c" and "d" only: "e" is eight days out, which is next week's problem.
        assertThat(digest.dueThisWeek).isEqualTo(2)
        assertThat(digest.isEmpty).isFalse()
    }

    @Test
    fun `a deferred task is still work owed`() {
        val digest = computeDueDigest(
            listOf(instance("s", due = today + 1, closes = today + 1, status = TaskStatus.SKIPPED)),
            today,
        )
        assertThat(digest.dueThisWeek).isEqualTo(1)
    }

    @Test
    fun `nothing due means an empty digest and no notification`() {
        val digest = computeDueDigest(
            listOf(instance("x", due = today + 90, closes = today + 90, status = TaskStatus.PENDING)),
            today,
        )
        assertThat(digest.isEmpty).isTrue()
    }

    @Test
    fun `the body template substitutes both counts and falls back to English`() {
        val digest = DueDigest(overdue = 3, dueThisWeek = 7)

        assertThat(NotificationContentBuilder.body(null, digest))
            .isEqualTo("3 overdue, 7 due this week")
        assertThat(NotificationContentBuilder.body("   ", digest))
            .isEqualTo("3 overdue, 7 due this week")
        assertThat(
            NotificationContentBuilder.body("{dueThisWeek} bu hafta, {overdue} gecikmiş", digest),
        ).isEqualTo("7 bu hafta, 3 gecikmiş")
        assertThat(NotificationContentBuilder.title(null))
            .isEqualTo(NotificationContentBuilder.DEFAULT_TITLE)
        assertThat(NotificationContentBuilder.title("DeckWatch — Due")).isEqualTo("DeckWatch — Due")
    }

    @Test
    fun `the daily job is delayed to the next 3am local`() {
        assertThat(WorkScheduler.initialDelayMinutes(LocalDateTime.of(2026, 3, 12, 1, 0)))
            .isEqualTo(120)
        assertThat(WorkScheduler.initialDelayMinutes(LocalDateTime.of(2026, 3, 12, 22, 30)))
            .isEqualTo(270)
        // Exactly 03:00 schedules tomorrow, so a run at 03:00 cannot fire twice.
        assertThat(WorkScheduler.initialDelayMinutes(LocalDateTime.of(2026, 3, 12, 3, 0)))
            .isEqualTo(24 * 60)
    }

    @Test
    fun `the scheduler hands the worker the strings it needs`() {
        val data = WorkScheduler.notificationData("Başlık", "{overdue} gecikmiş")

        assertThat(data.getString(DueRecomputeWorker.KEY_TITLE)).isEqualTo("Başlık")
        assertThat(data.getString(DueRecomputeWorker.KEY_BODY_TEMPLATE))
            .isEqualTo("{overdue} gecikmiş")
        assertThat(WorkScheduler.notificationData(null, null).getString(DueRecomputeWorker.KEY_TITLE))
            .isEqualTo(NotificationContentBuilder.DEFAULT_TITLE)
    }

    private fun instance(id: String, due: Long, closes: Long, status: TaskStatus) =
        TestData.taskInstance(
            id = id,
            dueDate = due,
            windowOpens = due,
            windowCloses = closes,
            status = status,
        )
}
