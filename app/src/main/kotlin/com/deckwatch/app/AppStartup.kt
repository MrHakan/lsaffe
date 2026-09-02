package com.deckwatch.app

import android.content.Context
import com.deckwatch.app.reminders.ReminderScheduler
import com.deckwatch.app.reminders.Reminders
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.MaintenanceRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.repository.SeedInitializer
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
 * 4. **Create the notification channels** with localised names (§11.3),
 * 5. **Schedule the 03:00 recomputation** (§11.2) and re-arm the daily digest at the officer's own
 *    hour (§11.3), and
 * 6. **Re-arm the weekly backup** if — and only if — a backup folder is set (§18).
 *
 * Step 4 exists here rather than in `data-repository` because C8 puts every user-visible string in
 * the app module's `strings.xml`. `Reminders` creates the channels defensively before every post if
 * it has to; creating them here first means the names the officer sees in Android's own
 * notification settings are in their language. The channel **ids** are fixed in `Reminders` and
 * never change — that is what carries the user's own importance and sound choices across updates.
 *
 * ### Why the digest is re-armed on every cold start
 *
 * WorkManager loses its queue when the app is force-stopped or reinstalled, and the officer should
 * not have to open settings and toggle the reminder to get it back. [ReminderScheduler.apply] is
 * idempotent — it replaces the pending request or cancels it, according to the stored preference.
 * Two schedules are involved and they are deliberately separate: the recomputation is fixed at
 * 03:00 because it is about the date boundary, while the digest fires at the hour the officer chose
 * (§18, default 08:00) because it is about them.
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
            runCatching { Reminders.createChannels(context) }
            runCatching { WorkScheduler.scheduleDaily(context) }
            runCatching {
                val prefs = preferences.get()
                ReminderScheduler.apply(
                    context = context,
                    enabled = prefs.notificationsEnabled,
                    hour = prefs.notificationHour,
                    minute = prefs.notificationMinute,
                )
            }
            // §18's weekly backup exists only while the officer has chosen a folder; re-arming it
            // here brings it back after a "clear app data" or a lost WorkManager database.
            runCatching { autoBackupScheduler.sync() }
        }
    }

    /**
     * Keep the daily digest armed at whatever hour the officer has chosen — §11.3, §18.
     *
     * The settings screen writes the preference and nothing else: `feature-settings` has no
     * business knowing that WorkManager exists, and the workers live in this module. So the queue
     * follows the preference from here instead, which also means a change made on one screen
     * cannot be forgotten by another that failed to call a scheduler.
     *
     * The first emission is skipped — [start] has already armed it from the stored values, and
     * re-arming immediately would cancel and re-enqueue the request for nothing.
     */
    suspend fun observeReminderSettings() {
        var first = true
        preferences.userPreferences
            .map { ReminderSettings(it.notificationsEnabled, it.notificationHour, it.notificationMinute) }
            .distinctUntilChanged()
            .collect { settings ->
                if (first) {
                    first = false
                    return@collect
                }
                runCatching {
                    ReminderScheduler.apply(
                        context = context,
                        enabled = settings.enabled,
                        hour = settings.hour,
                        minute = settings.minute,
                    )
                }
            }
    }

    private data class ReminderSettings(val enabled: Boolean, val hour: Int, val minute: Int)

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
}
