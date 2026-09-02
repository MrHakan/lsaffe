package com.deckwatch.data.repository.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.common.repository.VesselRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * The 03:00 daily recomputation — MASTER_PROMPT §11.2.
 *
 * ### What it recomputes, and why that choice
 *
 * **Every vessel, not just the active one.** The Due tab is explicitly a *cross-vessel* work list
 * (§12), an officer keeps the ship they are leaving alongside the one they are joining, and the
 * whole reason this job exists is the date boundary — which moves for every vessel at once, not
 * only for the selected one. A register is a few hundred rows and the recomputation is a single
 * transaction per vessel, so doing them all at 03:00 costs nothing anybody can feel.
 *
 * ### Why it does not notify
 *
 * This job is about the date boundary, and 03:00 is the wrong time to wake anyone. Telling the
 * officer what is due is `DueDigestWorker`'s job (§11.3), and it runs at the hour they chose in
 * §18 — by which point this job has already moved the register across the boundary, so the digest
 * counts what the Due tab will show them when they open it.
 *
 * ### Failure policy
 *
 * A database error is retried: the next attempt is cheap and idempotent, since recomputation is a
 * pure function of stored state.
 */
@HiltWorker
class DueRecomputeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val vesselRepository: VesselRepository,
    private val maintenanceRepository: MaintenanceRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val vessels = vesselRepository.observeVessels().first()
        for (vessel in vessels) {
            maintenanceRepository.recomputeDueForVessel(vessel.id)
        }
        Result.success()
    } catch (cancellation: CancellationException) {
        // WorkManager stopped us; cancellation is not a failure to report.
        throw cancellation
    } catch (_: Exception) {
        if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    private companion object {
        /** After this many failed attempts the run is abandoned; the next day's run tries again. */
        const val MAX_ATTEMPTS = 3
    }
}
