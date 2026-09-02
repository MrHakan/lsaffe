package com.deckwatch.data.repository.work

import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus

/**
 * The counts behind the daily digest notification — MASTER_PROMPT §11.3, "3 overdue, 7 due this
 * week".
 */
data class DueDigest(
    val overdue: Int = 0,
    val dueThisWeek: Int = 0,
) {
    /** Nothing to tell the officer about; the worker posts no notification. */
    val isEmpty: Boolean get() = overdue == 0 && dueThisWeek == 0
}

/**
 * Reduce open task instances to the digest counts.
 *
 * * **Overdue** — past `windowCloses`, i.e. the work is late even allowing for the task's own
 *   tolerance (§11.4). The stored status is used when it already says `OVERDUE`, and the window is
 *   re-checked against [todayEpochDay] as well, so an item that crossed the date boundary since the
 *   last recomputation is still counted correctly if the recomputation has not landed yet.
 * * **Due this week** — not overdue, and nominally due within the next seven days inclusive of
 *   today. A task that is merely inside a wide HSSC tolerance window months ahead is *not* "due
 *   this week"; the digest is a nudge about the coming week, not a repeat of the Due tab.
 *
 * `DONE` and `NOT_APPLICABLE` never count. `SKIPPED` does: the officer deferred it, it is still
 * owed.
 */
fun computeDueDigest(instances: List<TaskInstance>, todayEpochDay: Long): DueDigest {
    var overdue = 0
    var dueThisWeek = 0
    for (instance in instances) {
        if (instance.status == TaskStatus.DONE || instance.status == TaskStatus.NOT_APPLICABLE) {
            continue
        }
        val isOverdue = instance.status == TaskStatus.OVERDUE || todayEpochDay > instance.windowCloses
        when {
            isOverdue -> overdue++
            instance.dueDate in todayEpochDay..(todayEpochDay + DAYS_IN_WEEK) -> dueThisWeek++
        }
    }
    return DueDigest(overdue = overdue, dueThisWeek = dueThisWeek)
}

private const val DAYS_IN_WEEK = 7L

/**
 * Builds the digest notification's text from strings supplied by the caller.
 *
 * This module has no `res/` and must not: every user-visible string in DeckWatch is localised in
 * the app module (C8, English + Turkish). The app passes its own `getString(...)` results into the
 * worker's input `Data`, and this builder only substitutes the counts. The English fallbacks exist
 * so a worker enqueued by an older app version — or by a test — still produces sensible text
 * instead of an empty notification.
 *
 * Placeholders are `{overdue}` and `{dueThisWeek}`, not `%1$d`, because they survive a round trip
 * through WorkManager's `Data` unambiguously and can be reordered by a translator.
 */
object NotificationContentBuilder {

    const val PLACEHOLDER_OVERDUE: String = "{overdue}"
    const val PLACEHOLDER_DUE_THIS_WEEK: String = "{dueThisWeek}"

    /** English fallback title — §11.3. */
    const val DEFAULT_TITLE: String = "DeckWatch"

    /** English fallback body — §11.3's "3 overdue, 7 due this week". */
    const val DEFAULT_BODY_TEMPLATE: String = "$PLACEHOLDER_OVERDUE overdue, $PLACEHOLDER_DUE_THIS_WEEK due this week"

    fun title(template: String?): String = template?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE

    fun body(template: String?, digest: DueDigest): String =
        (template?.takeIf { it.isNotBlank() } ?: DEFAULT_BODY_TEMPLATE)
            .replace(PLACEHOLDER_OVERDUE, digest.overdue.toString())
            .replace(PLACEHOLDER_DUE_THIS_WEEK, digest.dueThisWeek.toString())
}
