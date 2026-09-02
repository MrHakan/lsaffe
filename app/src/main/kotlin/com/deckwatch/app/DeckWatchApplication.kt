package com.deckwatch.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The application object — and, deliberately, nothing else.
 *
 * ### Why it implements [Configuration.Provider]
 *
 * `DueRecomputeWorker` (§11.2) is a `@HiltWorker`: its dependencies are injected, so WorkManager
 * must build it through a [HiltWorkerFactory] rather than by calling its constructor reflectively.
 * That means WorkManager has to be initialised **on demand, with this configuration** instead of by
 * the default `androidx.startup` provider — which is why `AndroidManifest.xml` removes
 * `WorkManagerInitializer` from the startup provider. The two changes go together: remove the
 * initialiser without this interface and the first `WorkManager.getInstance` throws; implement the
 * interface without removing the initialiser and the default configuration wins, the factory is
 * never consulted, and the worker fails at 03:00 with `Could not instantiate`.
 *
 * ### Nothing blocking in `onCreate`
 *
 * There is no `onCreate` override at all. §17.3 budgets 1.5 s for a cold start, and every
 * millisecond spent here is spent before the first frame can even be measured. Seeding, the due
 * recomputation and the work schedule all belong to [AppStartup], which the activity's view model
 * kicks off in a coroutine once there is a UI to show progress in.
 */
@HiltAndroidApp
class DeckWatchApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
