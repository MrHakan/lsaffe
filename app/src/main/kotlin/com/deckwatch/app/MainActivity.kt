package com.deckwatch.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckwatch.app.ui.DeckWatchApp
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appearanceViewModel: AppearanceViewModel by viewModels()

    /** Every resource and every locale-driven choice below this activity resolves in English. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The theme is a stored choice, not the system's dark setting: the bridge scheme has
            // no system equivalent, and an officer who picked it must not get a white screen
            // because Android thinks it is daytime.
            val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
            DeckWatchTheme(themeMode = appearance.themeMode, density = appearance.density) {
                DeckWatchApp()
            }
        }
    }
}
