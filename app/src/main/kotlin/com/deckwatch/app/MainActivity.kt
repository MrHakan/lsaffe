package com.deckwatch.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.deckwatch.app.reminders.Reminders
import com.deckwatch.app.ui.AppViewModel
import com.deckwatch.app.ui.DeckWatchApp
import com.deckwatch.app.ui.DueRoute
import com.deckwatch.app.ui.StartDestination
import com.deckwatch.app.ui.VesselRoute
import com.deckwatch.feature.settings.AppLocale
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity — MASTER_PROMPT §3 ("single-activity, MVVM").
 *
 * ### Splash
 *
 * `installSplashScreen()` must run **before** `super.onCreate`. The splash is held while the
 * settings DataStore is being read, so the app never paints one frame of the Day theme before
 * flipping to Night, nor one frame of the tab UI before flipping to onboarding. `AppViewModel.ready`
 * is what that condition reads. The theme swap back to `Theme.DeckWatch` is declared by
 * `postSplashScreenTheme` in `themes.xml`, not done here.
 *
 * ### Locale
 *
 * `attachBaseContext` wraps the base context in the chosen UI language. On API 33+ this is a no-op
 * because the framework's per-app language has already been applied — see `AppLocale` for the whole
 * argument, including why this app does not use `AppCompatDelegate`.
 *
 * ### The notification tap (§11.3)
 *
 * The digest notification carries [Reminders.EXTRA_OPEN_TAB]. Both entry points are handled:
 * `onCreate` for a cold launch from the notification, and `onNewIntent` for a tap while the app is
 * already running — without the second, tapping the notification on a warm app would just bring
 * the last screen forward and look broken.
 *
 * ### POST_NOTIFICATIONS
 *
 * Deliberately **not** requested here. §11.3 requires the app to be fully usable without it, and a
 * launch-time permission dialog on a tool like this is the fastest route to a permanent denial. It
 * is asked for in Settings, at the moment the officer turns the daily reminder on.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    /** Recomposed when a notification tap arrives while the activity is alive. */
    private var start by mutableStateOf(StartDestination())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.ready.value }

        start = startFor(intent)

        setContent {
            // The theme and density are applied inside the shell, not here: the shell is where the
            // §14 after-dark schedule resolves the stored choice into the mode actually shown, and
            // splitting that across two composables would give the app two answers to one question.
            DeckWatchApp(start = start)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        start = startFor(intent)
    }

    /**
     * The Due tab when the digest notification sent us here, otherwise the Vessel tab (§5).
     *
     * The nonce increments on every request so that a second tap on the notification — after the
     * officer has navigated elsewhere — is a new value and re-triggers the switch.
     */
    private fun startFor(intent: Intent?): StartDestination =
        if (intent?.getStringExtra(Reminders.EXTRA_OPEN_TAB) == Reminders.TAB_DUE) {
            StartDestination(route = DueRoute, nonce = ++requestCount)
        } else {
            StartDestination(route = VesselRoute, nonce = 0)
        }

    private var requestCount = 0
}
