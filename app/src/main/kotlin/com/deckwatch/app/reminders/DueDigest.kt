package com.deckwatch.app.reminders

import com.deckwatch.core.model.TaskInstance
import com.deckwatch.feature.inspection.DueBucketing
import com.deckwatch.feature.inspection.DueSegment

/**
 * What the daily reminder actually says — MASTER_PROMPT §11.3.
 *
 * Pure arithmetic over the open work list, with no Android in sight, so the rule "when is it worth
 * waking the officer" is provable on the JVM. The segments come from [DueBucketing], which is the
 * same classification the Due tab shows: a notification that disagrees with the tab it links to
 * would be worse than no notification at all.
 */
data class DueDigest(
    val overdue: Int,
    val thisWeek: Int,
) {
    val total: Int get() = overdue + thisWeek

    /**
     * Nothing overdue and nothing due inside the week is the normal state on a well-run ship, and
     * a daily "all clear" is how a notification channel gets muted. Silence is the feature.
     */
    val worthNotifying: Boolean get() = total > 0

    companion object {
        /**
         * Counts the open occurrences that need attention now: everything overdue, plus everything
         * whose date falls inside the next seven days. Later segments (this month, before survey,
         * planned) are deliberately excluded — they belong to the Due tab, not to a daily alert.
         */
        fun from(
            instances: List<TaskInstance>,
            todayEpochDay: Long,
            certExpiry: Long? = null,
        ): DueDigest {
            var overdue = 0
            var thisWeek = 0
            for (instance in instances) {
                when (DueBucketing.segmentOf(instance, todayEpochDay, certExpiry)) {
                    DueSegment.OVERDUE -> overdue++
                    DueSegment.THIS_WEEK -> thisWeek++
                    else -> Unit
                }
            }
            return DueDigest(overdue = overdue, thisWeek = thisWeek)
        }
    }
}
