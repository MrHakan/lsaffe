package com.deckwatch.feature.settings

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
import kotlinx.coroutines.launch

/** The appearance settings of §14 and §18 as the screen shows them. */
data class AppearanceSettingsState(
    val themeMode: ThemeMode = ThemeMode.DAY,
    val density: ListDensity = ListDensity.COMPACT,
)

/**
 * Writes the theme and density preferences.
 *
 * Nothing here applies them: the activity's theme reads the same preferences, so a change takes
 * effect the moment it is stored, everywhere at once, with no second path to keep in step.
 */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<AppearanceSettingsState> = preferences.userPreferences
        .map { AppearanceSettingsState(themeMode = it.themeMode, density = it.density) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppearanceSettingsState(),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDensity(density: ListDensity) {
        viewModelScope.launch { preferences.setDensity(density) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
