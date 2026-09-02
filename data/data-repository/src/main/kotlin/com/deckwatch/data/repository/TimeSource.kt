package com.deckwatch.data.repository

import com.deckwatch.core.common.Dates
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one piece of ambient state the repositories cannot be pure functions of: what time it is.
 *
 * Injecting it keeps every due-date test deterministic — a due engine driven by
 * `System.currentTimeMillis()` would produce a different answer on 31 January than on 1 February,
 * which is exactly the class of bug §11 must not have.
 *
 * Two units, deliberately, matching §6: **epoch-days** for anything that is a date (due dates,
 * service dates — so a timezone change can never shift one) and **epoch-millis** for `createdAt` /
 * `updatedAt` audit stamps.
 */
interface TimeSource {
    fun nowMillis(): Long
    fun todayEpochDay(): Long
}

/** The production clock. */
@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowMillis(): Long = Dates.nowMillis()
    override fun todayEpochDay(): Long = Dates.todayEpochDay()
}

/** A frozen clock for tests and for previewing a computation "as of" a given day. */
class FixedTimeSource(
    private val todayEpochDay: Long,
    private val nowMillis: Long = todayEpochDay * MILLIS_PER_DAY,
) : TimeSource {
    override fun nowMillis(): Long = nowMillis
    override fun todayEpochDay(): Long = todayEpochDay

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
