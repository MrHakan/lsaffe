package com.deckwatch.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * How the app looks: the theme and the list density of §18.
 *
 * Null while preferences are loading, so the first frame can follow the system's dark setting
 * rather than flashing the day theme at someone on a darkened bridge.
 */
data class AppearanceState(
    val themeMode: ThemeMode? = null,
    val density: ListDensity = ListDensity.COMPACT,
)

/**
 * Reads the appearance settings for the activity's theme.
 *
 * It lives in the app module because the theme wraps everything: a feature module could own the
 * setting, but only the activity can apply it, and splitting the read from the application would
 * mean two sources of truth for the same frame.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<AppearanceState> = preferences.userPreferences
        .map { AppearanceState(themeMode = it.themeMode, density = it.density) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppearanceState(),
        )
}
