package com.deckwatch.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * Arms and disarms the local reminders — §11.3.
 *
 * The daily digest is a **one-shot that re-arms itself** rather than a `PeriodicWorkRequest`.
 * Periodic work fires on an interval from when it was enqueued, so an 08:00 digest would slide
 * later every time the device dozed through its window, and would keep the old hour after the
 * officer changed the setting. A one-shot computed from the wall clock lands on the configured
 * time each day and follows the ship across time zones.
 */
object ReminderScheduler {

    /**
     * (Re)schedules the digest for the next occurrence of [hour]:[minute].
     *
     * [ExistingWorkPolicy.REPLACE] is deliberate: changing the time in settings must move the
     * pending job, not queue a second one behind it.
     */
    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val request = OneTimeWorkRequestBuilder<DueDigestWorker>()
            .setInitialDelay(Reminders.delayUntilNext(hour, minute))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DueDigestWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Stops the digest. Called when the officer turns notifications off — §18. */
    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DueDigestWorker.WORK_NAME)
    }

    /**
     * Applies the current setting: armed when on, cancelled when off. This is the single place the
     * app decides whether the digest exists, so a stale job cannot outlive a disabled setting.
     */
    fun apply(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) scheduleDaily(context, hour, minute) else cancelDaily(context)
    }

    /**
     * A one-off reminder against a single item — the "remind me" of §11.3.
     *
     * Keyed by equipment id so setting a new reminder for the same item replaces the old one; two
     * reminders for one extinguisher is a mistake, not a feature.
     */
    fun scheduleItemReminder(context: Context, equipmentId: String, tag: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<ItemReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(
                Data.Builder()
                    .putString(ItemReminderWorker.KEY_EQUIPMENT_ID, equipmentId)
                    .putString(ItemReminderWorker.KEY_TAG, tag)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(itemWorkName(equipmentId), ExistingWorkPolicy.REPLACE, request)
    }

    /** Drops a pending reminder for one item. */
    fun cancelItemReminder(context: Context, equipmentId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(itemWorkName(equipmentId))
    }

    internal fun itemWorkName(equipmentId: String): String = "item_reminder_$equipmentId"
}
