package com.deckwatch.data.repository.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Enqueues the daily due recomputation — MASTER_PROMPT §11.2.
 *
 * One unique periodic request named [UNIQUE_WORK_NAME], targeted at **03:00 local time**: late
 * enough that the previous day's work is finished, early enough that the officer's morning digest
 * (default 08:00, §11.3) is computed from a register that already crossed the date boundary.
 *
 * WorkManager cannot promise an exact time — a periodic request fires within its window, and Doze
 * may push it later — so the initial delay is computed to the next 03:00 and the period is 24 hours
 * from there. The recomputation is idempotent, so an early, late or repeated run is harmless.
 *
 * `ExistingPeriodicWorkPolicy.UPDATE` means calling this on every app start is safe: it keeps the
 * pending run rather than cancelling and re-enqueuing it.
 */
object WorkScheduler {

    /** The unique work name. Stable — changing it would orphan the enqueued request. */
    const val UNIQUE_WORK_NAME: String = "deckwatch-due-recompute"

    /** Local hour the daily recomputation runs — §11.2. */
    const val DAILY_HOUR: Int = 3

    /** Schedule (or update) the daily recomputation. */
    fun scheduleDaily(
        context: Context,
        now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
    ) = scheduleDaily(
        workManager = WorkManager.getInstance(context.applicationContext),
        now = now,
    )

    /** The [WorkManager]-taking form, so a test can drive it without a `Context`. */
    fun scheduleDaily(
        workManager: WorkManager,
        now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
    ) {
        val request = PeriodicWorkRequestBuilder<DueRecomputeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(now), TimeUnit.MINUTES)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) = cancel(WorkManager.getInstance(context.applicationContext))

    fun cancel(workManager: WorkManager) {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * Minutes from [now] until the next [DAILY_HOUR] o'clock local. Exactly 03:00 counts as
     * tomorrow, so a scheduler that runs at 03:00:00 does not enqueue a zero delay and fire twice.
     */
    fun initialDelayMinutes(now: LocalDateTime): Long {
        val todayRun = LocalDateTime.of(now.toLocalDate(), LocalTime.of(DAILY_HOUR, 0))
        val next = if (now.isBefore(todayRun)) todayRun else todayRun.plusDays(1)
        return java.time.Duration.between(now, next).toMinutes()
    }

    /** Convenience for callers that only have a date and a wall-clock time. */
    fun initialDelayMinutes(date: LocalDate, time: LocalTime): Long =
        initialDelayMinutes(LocalDateTime.of(date, time))
}
