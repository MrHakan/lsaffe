package com.deckwatch.data.repository.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deckwatch.core.common.Dates
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.TaskInstance
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The 03:00 daily recomputation and digest notification — MASTER_PROMPT §11.2, §11.3.
 *
 * ### What it recomputes, and why that choice
 *
 * **Every vessel, not just the active one.** The Due tab is explicitly a *cross-vessel* work list
 * (§12), an officer keeps the ship they are leaving alongside the one they are joining, and the
 * whole reason this job exists is the date boundary — which moves for every vessel at once, not
 * only for the selected one. A register is a few hundred rows and the recomputation is a single
 * transaction per vessel, so doing them all at 03:00 costs nothing anybody can feel. The digest is
 * likewise counted across all vessels.
 *
 * ### Strings
 *
 * This module has no resources (C8 puts every user-visible string in the app's `strings.xml`), so
 * the app passes the localised title and body template in the worker's input `Data` — see
 * [KEY_TITLE] and [KEY_BODY_TEMPLATE], and `WorkScheduler.notificationData`. With no input the
 * worker falls back to [NotificationContentBuilder]'s English text rather than posting nothing.
 *
 * ### Failure policy
 *
 * The recomputation is the important half. A notification that cannot be posted (permission
 * denied — §11.3 requires the app to stay fully usable) is *not* a failure. A database error is
 * retried: the next attempt is cheap and idempotent, since recomputation is a pure function of
 * stored state.
 */
@HiltWorker
class DueRecomputeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val vesselRepository: VesselRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val preferences: UserPreferencesRepository,
    private val notificationPoster: NotificationPoster,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val vessels = vesselRepository.observeVessels().first()
        val instances = ArrayList<TaskInstance>()
        for (vessel in vessels) {
            maintenanceRepository.recomputeDueForVessel(vessel.id)
            instances += maintenanceRepository.observeOpenInstancesForVessel(vessel.id).first()
        }
        postDigestIfWanted(instances)
        Result.success()
    } catch (cancellation: CancellationException) {
        // WorkManager stopped us; cancellation is not a failure to report.
        throw cancellation
    } catch (_: Exception) {
        if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    private suspend fun postDigestIfWanted(instances: List<TaskInstance>) {
        if (!preferences.get().notificationsEnabled) return
        val digest = computeDueDigest(instances, Dates.todayEpochDay())
        if (digest.isEmpty) return
        notificationPoster.postDueDigest(
            title = NotificationContentBuilder.title(inputData.getString(KEY_TITLE)),
            body = NotificationContentBuilder.body(inputData.getString(KEY_BODY_TEMPLATE), digest),
        )
    }

    companion object {
        /** Localised notification title, supplied by the app. */
        const val KEY_TITLE: String = "title"

        /**
         * Localised body template containing [NotificationContentBuilder.PLACEHOLDER_OVERDUE] and
         * [NotificationContentBuilder.PLACEHOLDER_DUE_THIS_WEEK].
         */
        const val KEY_BODY_TEMPLATE: String = "bodyTemplate"

        /** After this many failed attempts the run is abandoned; the next day's run tries again. */
        private const val MAX_ATTEMPTS = 3
    }
}
