package com.deckwatch.feature.settings.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The automatic weekly backup of MASTER_PROMPT §18 — *"an automatic weekly backup to a user-chosen
 * SAF folder, keeping the last 8"*.
 *
 * ### It only exists while a folder is chosen
 *
 * There is no default destination and there never will be one: a backup written somewhere the
 * officer did not pick is a copy of the ship's safety register in a place nobody knows about.
 * [AutoBackupScheduler] enqueues this worker when a folder is set and cancels it when the folder is
 * cleared or its SAF grant goes away, and the worker itself re-checks before writing — a folder can
 * be revoked between two runs (an SD card out, or the user clearing app permissions), and finding
 * that at 03:00 must be a quiet no-op, not a failure notification.
 *
 * ### Automatic backups are never passphrase-protected
 *
 * A passphrase cannot be stored without becoming a key that is stored next to the thing it locks,
 * and a background job cannot ask for one. So the weekly file is a plain zip. That is stated here
 * rather than implied: the officer who wants encryption takes a manual backup and types a
 * passphrase, and the automatic one is a safety net against a lost phone, not against a lost
 * folder.
 */
@HiltWorker
class WeeklyBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val backupFolder: BackupFolder,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val tree = backupFolder.current()
        when {
            // No folder, or the grant has gone. Nothing to do and nothing to report.
            tree == null -> Result.success()
            else -> when (backupManager.autoBackup(tree)) {
                is BackupOutcome.Written, BackupOutcome.NothingToBackUp -> Result.success()
                is BackupOutcome.Failed ->
                    if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    companion object {
        /** Stable: changing it would orphan the enqueued request. */
        const val UNIQUE_WORK_NAME: String = "deckwatch-weekly-backup"

        private const val MAX_ATTEMPTS = 3
    }
}

/**
 * Keeps the weekly job in step with whether a backup folder is set.
 *
 * Called from two places: the More tab, the moment the officer picks or clears a folder, and the
 * app's cold-start coordinator, so a job that was cancelled by a "clear app data" or lost to an
 * uninstalled work database comes back.
 *
 * `ExistingPeriodicWorkPolicy.KEEP` on the enqueue is what stops a cold start from resetting the
 * week's countdown — with `UPDATE`, an officer who opens the app every morning would never reach
 * the end of the first period and no backup would ever be written.
 */
@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupFolder: BackupFolder,
) {

    /** Enqueue the weekly job when a folder is set; cancel it when there is none. */
    fun sync() {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (backupFolder.current() == null) {
            workManager.cancelUniqueWork(WeeklyBackupWorker.UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeeklyBackupWorker>(WEEKLY_DAYS, TimeUnit.DAYS)
            .addTag(WeeklyBackupWorker.UNIQUE_WORK_NAME)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WeeklyBackupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WEEKLY_DAYS = 7L
    }
}
