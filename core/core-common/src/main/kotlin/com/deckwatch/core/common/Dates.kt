package com.deckwatch.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * All *dates* in DeckWatch (service dates, due dates) are epoch-days so
 * timezone drift never shifts a due date — §6.
 */
object Dates {

    fun todayEpochDay(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    fun nowMillis(): Long = Instant.now().toEpochMilli()

    /** Add whole calendar months to an epoch-day, clamping to end of month. */
    fun plusMonths(epochDay: Long, months: Int): Long =
        LocalDate.ofEpochDay(epochDay).plusMonths(months.toLong()).toEpochDay()

    fun plusDays(epochDay: Long, days: Int): Long = epochDay + days

    /** ISO-8601 display, e.g. 2026-03-12. */
    fun formatIso(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)

    /**
     * The next anniversary of [anchorEpochDay] that falls on or after [fromEpochDay].
     * Used for the HSSC survey-anniversary window — §11.1.
     */
    fun nextAnniversary(anchorEpochDay: Long, fromEpochDay: Long): Long {
        val anchor = LocalDate.ofEpochDay(anchorEpochDay)
        val from = LocalDate.ofEpochDay(fromEpochDay)
        var candidate = anchor.withYear(from.year)
        if (candidate.isBefore(from)) candidate = candidate.plusYears(1)
        return candidate.toEpochDay()
    }
}
