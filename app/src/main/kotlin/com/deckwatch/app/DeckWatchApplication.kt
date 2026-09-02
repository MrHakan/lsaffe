package com.deckwatch.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

/**
 * The application object — and, deliberately, almost nothing else.
 *
 * ### Why it implements [Configuration.Provider]
 *
 * `DueRecomputeWorker` (§11.2) and the reminder workers (§11.3) are `@HiltWorker`s: their
 * dependencies are injected, so WorkManager must build them through a [HiltWorkerFactory] rather
 * than by calling their constructors reflectively. That means WorkManager has to be initialised
 * **on demand, with this configuration** instead of by the default `androidx.startup` provider —
 * which is why `AndroidManifest.xml` removes `WorkManagerInitializer` from the startup provider.
 * The two changes go together: remove the initialiser without this interface and the first
 * `WorkManager.getInstance` throws; implement the interface without removing the initialiser and
 * the default configuration wins, the factory is never consulted, and the worker fails at 03:00
 * with `Could not instantiate`.
 *
 * ### Nothing blocking in `onCreate`
 *
 * The only work here is setting the default locale, which is a field write. §17.3 budgets 1.5 s for
 * a cold start, and every millisecond spent here is spent before the first frame can even be
 * measured. Seeding, the due recomputation, the notification channels and the work schedules all
 * belong to [AppStartup], which the activity's view model kicks off in a coroutine once there is a
 * UI to show progress in.
 */
@HiltAndroidApp
class DeckWatchApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Work that runs without an activity — the reminder workers — formats dates and text in
        // the app's language too. A field write, so it does not cost the cold-start budget.
        Locale.setDefault(AppLocale.current)
    }
}
