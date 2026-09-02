package com.deckwatch.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.repository.SeedInitializer
import com.deckwatch.data.repository.work.NotificationPoster
import com.deckwatch.data.repository.work.WorkScheduler
import com.deckwatch.feature.settings.backup.AutoBackupScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that has to happen once per cold start, in one place — MASTER_PROMPT §11.2, §19.
 *
 * ### The sequence
 *
 * 1. **Seed the bundled content** (`SeedInitializer.ensureSeeded`, §19). Idempotent and cheap when
 *    already current: it reads one stored version int and returns.
 * 2. **Record first run** (`markFirstRun`), which anchors the §18 day-30 backup prompt. Written
 *    once, ever — later calls are no-ops, so the counter never resets.
 * 3. **Recompute due state for the active vessel** (§11.2's "on app cold start"). Only the active
 *    one: the officer is about to look at it, and the 03:00 worker does every vessel anyway.
 * 4. **Create the notification channel** with a localised name,
 * 5. **Schedule the daily job** with the localised digest strings, and
 * 6. **Re-arm the weekly backup** if — and only if — a backup folder is set (§18).
 *
 * Steps 4 and 5 exist here rather than in `data-repository` because C8 puts every user-visible
 * string in the app module's `strings.xml`. `NotificationPoster` creates the channel defensively
 * with English text if it has to; creating it here first, with the officer's language, means the
 * name they see in Android's own notification settings is in their language. The channel **id** is
 * `NotificationPoster.CHANNEL_ID` and never changes — that is what carries the user's own
 * importance and sound choices across app updates.
 *
 * ### Vessel switching (§11.2)
 *
 * [observeActiveVessel] collects the active-vessel id and recomputes on every change. It is a
 * suspending collect that never returns, so the caller runs it in a scope that dies with the UI.
 *
 * ### Failure policy
 *
 * Every step is wrapped: a corrupt seed asset or an unwritable DataStore must not stop the app from
 * opening. The officer can still read their register, and the next cold start tries again.
 */
@Singleton
class AppStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seedInitializer: SeedInitializer,
    private val preferences: UserPreferencesRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val dispatchers: DispatcherProvider,
) {

    private val started = AtomicBoolean(false)

    /** Runs the cold-start sequence exactly once per process. */
    suspend fun start() {
        if (!started.compareAndSet(false, true)) return
        withContext(dispatchers.io) {
            runCatching { seedInitializer.ensureSeeded() }
            runCatching { preferences.markFirstRun(System.currentTimeMillis()) }
            runCatching {
                preferences.get().activeVesselId?.let { maintenanceRepository.recomputeDueForVessel(it) }
            }
            runCatching { ensureNotificationChannel() }
            runCatching { scheduleDailyDigest() }
            // §18's weekly backup exists only while the officer has chosen a folder; re-arming it
            // here brings it back after a "clear app data" or a lost WorkManager database.
            runCatching { autoBackupScheduler.sync() }
        }
    }

    /**
     * §11.2's "on vessel switch": recompute whenever the active vessel changes.
     *
     * The first emission is skipped — [start] has already recomputed for whatever vessel was active
     * at launch, and doing it twice on every cold start would double the work for nothing.
     */
    suspend fun observeActiveVessel() {
        var first = true
        preferences.userPreferences
            .map { it.activeVesselId }
            .distinctUntilChanged()
            .collect { vesselId ->
                if (first) {
                    first = false
                    return@collect
                }
                if (vesselId != null) {
                    runCatching {
                        withContext(dispatchers.io) { maintenanceRepository.recomputeDueForVessel(vesselId) }
                    }
                }
            }
    }

    /**
     * Create (or update the name of) the digest channel — §11.3.
     *
     * Re-creating a channel with an existing id only refreshes its name and description; the user's
     * own importance, sound and vibration choices are untouched. That is what makes it safe to call
     * on every cold start, which is how a language change reaches Android's settings UI.
     */
    private fun ensureNotificationChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NotificationPoster.CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_description) }
        manager.createNotificationChannel(channel)
    }

    /** Enqueue the 03:00 recomputation with this locale's digest strings — §11.2, §11.3. */
    private fun scheduleDailyDigest() {
        WorkScheduler.scheduleDaily(
            context = context,
            notificationTitle = context.getString(R.string.notification_digest_title),
            notificationBodyTemplate = context.getString(R.string.notification_digest_body),
        )
    }
}
