package com.deckwatch.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.deckwatch.app.reminders.ReminderScheduler
import com.deckwatch.app.reminders.Reminders
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.data.repository.ContentSeeder
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class DeckWatchApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var contentSeeder: ContentSeeder

    @Inject
    lateinit var preferences: UserPreferencesRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager is initialised on demand from here rather than by its default content provider,
     * which is what lets the reminder workers take repositories through Hilt.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /**
     * Application-scoped: the import must finish even if the activity that started it goes away,
     * and there is nothing to cancel it back to — the process ending is the cancellation.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Work that runs without an activity — the content import and the reminder workers —
        // formats dates and text in the app's language too.
        Locale.setDefault(AppLocale.current)
        Reminders.createChannels(this)
        appScope.launch {
            runCatching { contentSeeder.seedIfNeeded() }
                .onFailure { Log.e(TAG, "Bundled content import failed", it) }
            // Re-arm on every launch: WorkManager loses its queue when the app is force-stopped or
            // reinstalled, and the officer should not have to touch settings to get it back.
            runCatching {
                val prefs = preferences.get()
                ReminderScheduler.apply(
                    context = this@DeckWatchApplication,
                    enabled = prefs.notificationsEnabled,
                    hour = prefs.notificationHour,
                    minute = prefs.notificationMinute,
                )
            }.onFailure { Log.e(TAG, "Reminder scheduling failed", it) }
        }
    }

    private companion object {
        const val TAG = "DeckWatch"
    }
}
