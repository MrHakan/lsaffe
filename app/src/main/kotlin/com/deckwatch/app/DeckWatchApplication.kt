package com.deckwatch.app

import android.app.Application
import android.util.Log
import com.deckwatch.data.repository.ContentSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class DeckWatchApplication : Application() {

    @Inject
    lateinit var contentSeeder: ContentSeeder

    /**
     * Application-scoped: the import must finish even if the activity that started it goes away,
     * and there is nothing to cancel it back to — the process ending is the cancellation.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { contentSeeder.seedIfNeeded() }
                .onFailure { Log.e(TAG, "Bundled content import failed", it) }
        }
    }

    private companion object {
        const val TAG = "DeckWatch"
    }
}
