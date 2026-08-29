package com.deckwatch.core.common.due

import com.deckwatch.core.common.Dates
import com.deckwatch.core.model.IntervalKind
import com.deckwatch.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

/** §11.1 (2)–(3) — the interval rules. */
class DueEngineScheduleTest {

    private val today = day(2026, 1, 20)
    private val engine = DueEngine { today }

    private fun schedule(
        kind: IntervalKind,
        anchor: Long? = day(2026, 1, 15),
        intervalMonths: Int? = null,
        before: Int = 0,
        after: Int = 0,
        certExpiry: Long? = null,
        todayEpochDay: Long = today,
    ): DueComputation? = engine.computeSchedule(
        definition = taskDefinition(
            intervalKind = kind,
            intervalMonths = intervalMonths,
            toleranceDaysBefore = before,
            toleranceDaysAfter = after,
        ),
        lastCompletedEpochDay = anchor,
        installedDate = null,
        manufactureDate = null,
        certExpiryEpochDay = certExpiry,
        todayEpochDay = todayEpochDay,
    )

    // ---------------------------------------------------------------- intervals

    @Test
    fun `WEEKLY is exactly seven days after the anchor`() {
        assertThat(dateOf(requireNotNull(schedule(IntervalKind.WEEKLY)).dueDate))
            .isEqualTo(LocalDate.of(2026, 1, 22))
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "MONTHLY,        2026-02-15",
        "QUARTERLY,      2026-04-15",
        "ANNUAL,         2027-01-15",
        "BIENNIAL,       2028-01-15",
        "FIVE_YEARLY,    2031-01-15",
        "TEN_YEARLY,     2036-01-15",
        "TWENTY_YEARLY,  2046-01-15",
    )
    fun `calendar-month kinds add their month count to the anchor`(kind: IntervalKind, expected: String) {
        val computed = requireNotNull(schedule(kind))
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.parse(expected))
    }

    @Test
    fun `CUSTOM_MONTHS uses the definition's intervalMonths`() {
        val computed = requireNotNull(schedule(IntervalKind.CUSTOM_MONTHS, intervalMonths = 7))
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 8, 15))
    }

    @Test
    fun `CUSTOM_MONTHS without intervalMonths schedules nothing`() {
        assertThat(schedule(IntervalKind.CUSTOM_MONTHS, intervalMonths = null)).isNull()
    }

    @Test
    fun `CUSTOM_MONTHS without intervalMonths schedules nothing even with no anchor`() {
        assertThat(schedule(IntervalKind.CUSTOM_MONTHS, anchor = null, intervalMonths = null)).isNull()
    }

    @Test
    fun `EVENT_DRIVEN never schedules an instance`() {
        assertThat(schedule(IntervalKind.EVENT_DRIVEN)).isNull()
        assertThat(schedule(IntervalKind.EVENT_DRIVEN, anchor = null)).isNull()
    }

    @Test
    fun `every interval kind is handled without throwing`() {
        IntervalKind.entries.forEach { kind ->
            schedule(kind, intervalMonths = 4, certExpiry = day(2024, 3, 1))
        }
    }

    @EnumSource(
        value = IntervalKind::class,
        names = ["EVENT_DRIVEN", "AT_SURVEY"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    @ParameterizedTest
    fun `every scheduled kind produces a due date on or after the anchor`(kind: IntervalKind) {
        val computed = requireNotNull(schedule(kind, intervalMonths = 4))
        assertThat(computed.dueDate).isAtLeast(day(2026, 1, 15))
    }

    // ---------------------------------------------------------------- month-end clamping

    @Test
    fun `31 January plus one month clamps to 28 February`() {
        val computed = requireNotNull(
            schedule(IntervalKind.MONTHLY, anchor = day(2026, 1, 31)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `31 January plus one month clamps to 29 February in a leap year`() {
        val computed = requireNotNull(
            schedule(IntervalKind.MONTHLY, anchor = day(2024, 1, 31), todayEpochDay = day(2024, 2, 1)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2024, 2, 29))
    }

    @Test
    fun `29 February plus twelve months clamps to 28 February`() {
        val computed = requireNotNull(
            schedule(IntervalKind.ANNUAL, anchor = day(2024, 2, 29), todayEpochDay = day(2024, 3, 1)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2025, 2, 28))
    }

    @Test
    fun `31 August plus one month clamps to 30 September`() {
        val computed = requireNotNull(
            schedule(IntervalKind.MONTHLY, anchor = day(2026, 8, 31), todayEpochDay = day(2026, 9, 1)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun `WEEKLY does not clamp - it crosses the month boundary`() {
        val computed = requireNotNull(schedule(IntervalKind.WEEKLY, anchor = day(2026, 1, 31)))
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 2, 7))
    }

    // ---------------------------------------------------------------- tolerance windows

    @Test
    fun `tolerances open and close the window around the due date`() {
        val computed = requireNotNull(schedule(IntervalKind.MONTHLY, before = 10, after = 5))
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 2, 15))
        assertThat(dateOf(computed.windowOpens)).isEqualTo(LocalDate.of(2026, 2, 5))
        assertThat(dateOf(computed.windowCloses)).isEqualTo(LocalDate.of(2026, 2, 20))
        assertThat(computed.windowLengthDays).isEqualTo(15L)
    }

    @Test
    fun `zero tolerance collapses the window onto the due date`() {
        val computed = requireNotNull(schedule(IntervalKind.MONTHLY))
        assertThat(computed.windowOpens).isEqualTo(computed.dueDate)
        assertThat(computed.windowCloses).isEqualTo(computed.dueDate)
        assertThat(computed.windowLengthDays).isEqualTo(0L)
    }

    @Test
    fun `negative tolerances in seed data are clamped so the window cannot invert`() {
        val computed = requireNotNull(schedule(IntervalKind.MONTHLY, before = -30, after = -30))
        assertThat(computed.windowOpens).isEqualTo(computed.dueDate)
        assertThat(computed.windowCloses).isEqualTo(computed.dueDate)
    }

    @Test
    fun `the HSSC three-month tolerance is expressed in days`() {
        val computed = requireNotNull(schedule(IntervalKind.ANNUAL, before = 90, after = 90))
        assertThat(computed.dueDate - computed.windowOpens).isEqualTo(90L)
        assertThat(computed.windowCloses - computed.dueDate).isEqualTo(90L)
    }

    // ---------------------------------------------------------------- AT_SURVEY

    @Test
    fun `AT_SURVEY due date is the next certificate anniversary on or after today`() {
        val computed = requireNotNull(
            schedule(IntervalKind.AT_SURVEY, certExpiry = day(2020, 6, 15), todayEpochDay = day(2026, 8, 1)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2027, 6, 15))
    }

    @Test
    fun `AT_SURVEY keeps a same-year anniversary that is still ahead`() {
        val computed = requireNotNull(
            schedule(IntervalKind.AT_SURVEY, certExpiry = day(2020, 11, 20), todayEpochDay = day(2026, 8, 1)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 11, 20))
    }

    @Test
    fun `AT_SURVEY on the anniversary itself is due today`() {
        val computed = requireNotNull(
            schedule(IntervalKind.AT_SURVEY, certExpiry = day(2020, 6, 15), todayEpochDay = day(2026, 6, 15)),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2026, 6, 15))
    }

    @Test
    fun `AT_SURVEY window is plus or minus 90 days even with no tolerances declared`() {
        val computed = requireNotNull(
            schedule(IntervalKind.AT_SURVEY, certExpiry = day(2020, 6, 15), todayEpochDay = day(2026, 8, 1)),
        )
        assertThat(computed.dueDate - computed.windowOpens).isEqualTo(90L)
        assertThat(computed.windowCloses - computed.dueDate).isEqualTo(90L)
    }

    @Test
    fun `AT_SURVEY keeps wider definition tolerances`() {
        val computed = requireNotNull(
            schedule(
                IntervalKind.AT_SURVEY,
                before = 120,
                after = 200,
                certExpiry = day(2020, 6, 15),
                todayEpochDay = day(2026, 8, 1),
            ),
        )
        assertThat(computed.dueDate - computed.windowOpens).isEqualTo(120L)
        assertThat(computed.windowCloses - computed.dueDate).isEqualTo(200L)
    }

    @Test
    fun `AT_SURVEY ignores narrower definition tolerances`() {
        val computed = requireNotNull(
            schedule(
                IntervalKind.AT_SURVEY,
                before = 5,
                after = 5,
                certExpiry = day(2020, 6, 15),
                todayEpochDay = day(2026, 8, 1),
            ),
        )
        assertThat(computed.dueDate - computed.windowOpens).isEqualTo(90L)
        assertThat(computed.windowCloses - computed.dueDate).isEqualTo(90L)
    }

    @Test
    fun `AT_SURVEY with no certificate expiry schedules nothing`() {
        assertThat(schedule(IntervalKind.AT_SURVEY, certExpiry = null)).isNull()
    }

    @Test
    fun `AT_SURVEY ignores the completion anchor entirely`() {
        val withAnchor = schedule(
            IntervalKind.AT_SURVEY,
            anchor = day(2001, 1, 1),
            certExpiry = day(2020, 6, 15),
            todayEpochDay = day(2026, 8, 1),
        )
        val withoutAnchor = schedule(
            IntervalKind.AT_SURVEY,
            anchor = null,
            certExpiry = day(2020, 6, 15),
            todayEpochDay = day(2026, 8, 1),
        )
        assertThat(withAnchor).isEqualTo(withoutAnchor)
    }

    // ---------------------------------------------------------------- anchor precedence

    @Test
    fun `the last completion beats installed and manufacture dates`() {
        val computed = requireNotNull(
            engine.computeSchedule(
                definition = taskDefinition(intervalKind = IntervalKind.ANNUAL),
                lastCompletedEpochDay = day(2026, 1, 15),
                installedDate = day(2020, 5, 5),
                manufactureDate = day(2018, 3, 3),
                certExpiryEpochDay = null,
            ),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2027, 1, 15))
    }

    @Test
    fun `the installed date is used when there is no completion`() {
        val computed = requireNotNull(
            engine.computeSchedule(
                definition = taskDefinition(intervalKind = IntervalKind.ANNUAL),
                lastCompletedEpochDay = null,
                installedDate = day(2020, 5, 5),
                manufactureDate = day(2018, 3, 3),
                certExpiryEpochDay = null,
            ),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2021, 5, 5))
    }

    @Test
    fun `the manufacture date is the last resort anchor`() {
        val computed = requireNotNull(
            engine.computeSchedule(
                definition = taskDefinition(intervalKind = IntervalKind.TEN_YEARLY),
                lastCompletedEpochDay = null,
                installedDate = null,
                manufactureDate = day(2018, 3, 3),
                certExpiryEpochDay = null,
            ),
        )
        assertThat(dateOf(computed.dueDate)).isEqualTo(LocalDate.of(2028, 3, 3))
    }

    // ---------------------------------------------------------------- no-anchor baseline

    @Test
    fun `with no anchor at all the item is due today so the first record gets established`() {
        val computed = requireNotNull(schedule(IntervalKind.ANNUAL, anchor = null))
        assertThat(computed.dueDate).isEqualTo(today)
    }

    @Test
    fun `the no-anchor baseline still carries the tolerance window`() {
        val computed = requireNotNull(schedule(IntervalKind.ANNUAL, anchor = null, before = 10, after = 5))
        assertThat(computed.windowOpens).isEqualTo(today - 10)
        assertThat(computed.windowCloses).isEqualTo(today + 5)
    }

    @Test
    fun `the no-anchor baseline classifies as due soon not overdue`() {
        val computed = requireNotNull(schedule(IntervalKind.ANNUAL, anchor = null))
        assertThat(engine.classify(computed, today)).isEqualTo(TaskStatus.DUE_SOON)
    }

    @Test
    fun `the injected clock supplies today when no date is passed`() {
        val computed = requireNotNull(
            engine.computeSchedule(
                definition = taskDefinition(intervalKind = IntervalKind.MONTHLY),
                lastCompletedEpochDay = null,
                installedDate = null,
                manufactureDate = null,
                certExpiryEpochDay = null,
            ),
        )
        assertThat(computed.dueDate).isEqualTo(today)
    }

    @Test
    fun `the default engine reads the system clock`() {
        val computed = requireNotNull(
            DueEngine().computeSchedule(
                definition = taskDefinition(intervalKind = IntervalKind.MONTHLY),
                lastCompletedEpochDay = null,
                installedDate = null,
                manufactureDate = null,
                certExpiryEpochDay = null,
            ),
        )
        assertThat(computed.dueDate).isEqualTo(Dates.todayEpochDay())
    }
}
