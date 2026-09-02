package com.deckwatch.data.repository.work

import android.content.Context
import androidx.work.Data
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
 * `ExistingPeriodicWorkPolicy.UPDATE` means calling this on every app start is safe and picks up
 * new notification strings (a language change) without cancelling the pending run.
 */
object WorkScheduler {

    /** The unique work name. Stable — changing it would orphan the enqueued request. */
    const val UNIQUE_WORK_NAME: String = "deckwatch-due-recompute"

    /** Local hour the daily recomputation runs — §11.2. */
    const val DAILY_HOUR: Int = 3

    /**
     * Schedule (or update) the daily job.
     *
     * @param notificationTitle the app's localised notification title; null uses the English
     *   fallback in [NotificationContentBuilder].
     * @param notificationBodyTemplate the app's localised body containing
     *   `{overdue}` and `{dueThisWeek}`; null uses the English fallback.
     */
    fun scheduleDaily(
        context: Context,
        notificationTitle: String? = null,
        notificationBodyTemplate: String? = null,
        now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
    ) = scheduleDaily(
        workManager = WorkManager.getInstance(context.applicationContext),
        notificationTitle = notificationTitle,
        notificationBodyTemplate = notificationBodyTemplate,
        now = now,
    )

    /** The [WorkManager]-taking form, so a test can drive it without a `Context`. */
    fun scheduleDaily(
        workManager: WorkManager,
        notificationTitle: String? = null,
        notificationBodyTemplate: String? = null,
        now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault()),
    ) {
        val request = PeriodicWorkRequestBuilder<DueRecomputeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(now), TimeUnit.MINUTES)
            .setInputData(notificationData(notificationTitle, notificationBodyTemplate))
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

    /** The strings the worker needs, as WorkManager input data. */
    fun notificationData(title: String?, bodyTemplate: String?): Data = Data.Builder()
        .putString(DueRecomputeWorker.KEY_TITLE, title ?: NotificationContentBuilder.DEFAULT_TITLE)
        .putString(
            DueRecomputeWorker.KEY_BODY_TEMPLATE,
            bodyTemplate ?: NotificationContentBuilder.DEFAULT_BODY_TEMPLATE,
        )
        .build()

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
