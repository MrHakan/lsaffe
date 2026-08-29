package com.deckwatch.feature.inspection

import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.TaskStatus

/**
 * The segment and filter rules of the Due tab — §12. Pure functions of their arguments with no
 * clock and no Android dependency, so every boundary is unit-testable against a fixed today.
 */
object DueBucketing {

    /** "This week" reaches seven days ahead, inclusive. */
    const val WEEK_DAYS: Long = 7

    /** "This month" reaches thirty days ahead, inclusive — the same horizon as the due engine's default lead time. */
    const val MONTH_DAYS: Long = 30

    /**
     * Which segment one open occurrence belongs to, evaluated in this order — §12, §11.4:
     *
     * 1. **Deferred** (`status == SKIPPED`) → [DueSegment.PLANNED]. A deferral is a deliberate
     *    parking of the job, so it leaves the urgent segments immediately and stays visible in
     *    Planned rather than vanishing from the work list.
     * 2. **Overdue** — the engine said [TaskStatus.OVERDUE], or the tolerance window has closed
     *    (`windowCloses < today`). The engine's classification wins because it already applied the
     *    HSSC ±3-month rule; the window check is the belt-and-braces fallback for an instance the
     *    engine has not re-classified since the date rolled over (§11.2 runs it daily at 03:00).
     * 3. **This week** — `dueDate <= today + 7`. This deliberately also catches an occurrence whose
     *    nominal date has passed but whose tolerance window is still open: it is not overdue, but it
     *    is the most urgent thing on the list.
     * 4. **This month** — `dueDate <= today + 30`.
     * 5. **Before next survey** — a Safety Equipment Certificate expiry is on file and
     *    `dueDate <= certExpiry`.
     * 6. **Planned** — everything else, including everything beyond the certificate expiry and,
     *    when no expiry is recorded, everything past 30 days.
     *
     * The six rules partition the open list, so the chip counts sum to the total.
     */
    fun segmentOf(instance: TaskInstance, todayEpochDay: Long, certExpiry: Long?): DueSegment = when {
        instance.status == TaskStatus.SKIPPED -> DueSegment.PLANNED
        instance.status == TaskStatus.OVERDUE || instance.windowCloses < todayEpochDay -> DueSegment.OVERDUE
        instance.dueDate <= todayEpochDay + WEEK_DAYS -> DueSegment.THIS_WEEK
        instance.dueDate <= todayEpochDay + MONTH_DAYS -> DueSegment.THIS_MONTH
        certExpiry != null && instance.dueDate <= certExpiry -> DueSegment.BEFORE_SURVEY
        else -> DueSegment.PLANNED
    }

    /**
     * Apply the combined filter set — §12. Dimensions AND together; a `null` dimension passes
     * everything.
     *
     * @param categoryMembership equipment id -> the logical categories it carries (§6.4). Only
     *   consulted when [DueFilters.categoryId] is set, so the caller need not load the cross-
     *   reference table for the common unfiltered case.
     */
    fun applyFilters(
        rows: List<DueRow>,
        filters: DueFilters,
        categoryMembership: Map<String, Set<String>> = emptyMap(),
    ): List<DueRow> {
        if (!filters.isActive) return rows
        return rows.filter { row ->
            (filters.deckId == null || row.deckId == filters.deckId) &&
                (filters.zoneId == null || row.zoneId == filters.zoneId) &&
                (filters.group == null || row.group == filters.group) &&
                (filters.performedBy == null || row.performedBy == filters.performedBy) &&
                (filters.condition == null || row.condition == filters.condition) &&
                (
                    filters.categoryId == null ||
                        categoryMembership[row.equipmentId]?.contains(filters.categoryId) == true
                    )
        }
    }

    /** Counts per segment across the whole filtered list — the numbers on the segment chips. */
    fun countBySegment(rows: List<DueRow>): Map<DueSegment, Int> =
        DueSegment.entries.associateWith { segment -> rows.count { it.segment == segment } }

    /**
     * Survey prep — §12. Everything still open that falls due on or before [certExpiry], **including
     * work already overdue**, because an overdue job is exactly what a surveyor will ask about. The
     * segments partition the list for the work view; survey prep deliberately cuts across them.
     *
     * @param rows already filtered rows, so the officer's deck/group filters carry into survey prep.
     */
    fun surveyPrep(rows: List<DueRow>, certExpiry: Long, todayEpochDay: Long): SurveyPrepState {
        val inScope = rows.filter { it.dueDate <= certExpiry }.sortedBy { it.dueDate }
        val (ship, shore) = inScope.partition { it.performedBy.isShipStaff }
        return SurveyPrepState(
            certExpiry = certExpiry,
            daysToExpiry = certExpiry - todayEpochDay,
            shipStaff = ship,
            shoreProvider = shore,
            shoppingList = shoppingList(shore),
        )
    }

    /**
     * The shore-service shopping list: distinct task titles with the number of items needing each,
     * heaviest first — what goes to the agent before the next port (§12).
     */
    fun shoppingList(shoreRows: List<DueRow>): List<ShoreServiceItem> =
        shoreRows
            .groupBy { it.taskKey }
            .map { (taskKey, group) ->
                val first = group.first()
                ShoreServiceItem(
                    taskKey = taskKey,
                    title = first.taskTitle,
                    performedBy = first.performedBy,
                    count = group.size,
                )
            }
            .sortedWith(compareByDescending<ShoreServiceItem> { it.count }.thenBy { it.title.en })

    /**
     * Work-list order: most urgent first. Overdue leads, then by due date, then by tag so two jobs
     * falling on the same day read in a stable order.
     */
    fun sortForWorkList(rows: List<DueRow>): List<DueRow> =
        rows.sortedWith(compareBy<DueRow> { it.dueDate }.thenBy { it.tag }.thenBy { it.taskKey })
}
