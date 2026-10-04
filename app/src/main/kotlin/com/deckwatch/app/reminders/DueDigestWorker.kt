package com.deckwatch.app.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The daily reminder job — §11.3.
 *
 * Reads the active vessel's open work list, counts what is overdue or due this week, and posts a
 * single summary. It reschedules itself for the next configured time on the way out, which is what
 * makes the digest follow the device clock across time zones instead of drifting by the
 * accumulated error of a fixed 24-hour period.
 *
 * Every early return is a normal state, not a failure: no active vessel yet, notifications turned
 * off, permission refused, nothing due. [Result.success] in those cases keeps WorkManager from
 * retrying with backoff over something that is simply not applicable today.
 */
@HiltWorker
class DueDigestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferencesRepository,
    private val vessels: VesselRepository,
    private val maintenance: MaintenanceRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val prefs = preferences.get()
        if (prefs.notificationsEnabled) {
            postDigest(prefs.activeVesselId)
            // Append only after a successful read/post: failed prerequisites would fail tomorrow too.
            // REPLACE would cancel this worker while it is finishing today's digest.
            ReminderScheduler.scheduleNextDaily(applicationContext, prefs.notificationHour, prefs.notificationMinute)
        }
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // Keep this occurrence retryable without appending another successor on each attempt.
        Result.retry()
    }

    private suspend fun postDigest(vesselId: String?) {
        if (vesselId == null) return
        val vessel = vessels.getVessel(vesselId) ?: return

        val instances = maintenance.observeOpenInstancesForVessel(vesselId).first()
        val digest = DueDigest.from(
            instances = instances,
            todayEpochDay = Reminders.todayEpochDay(),
            certExpiry = vessel.safetyEquipmentCertExpiry,
        )
        Reminders.postDigest(applicationContext, digest)
    }

    companion object {
        /** Unique work name; also the handle [ReminderScheduler] cancels by. */
        const val WORK_NAME: String = "due_digest"
    }
}
