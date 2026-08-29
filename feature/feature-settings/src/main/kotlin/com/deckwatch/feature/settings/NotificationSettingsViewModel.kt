package com.deckwatch.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The notification settings of §18: whether the daily digest runs, and at what hour. */
data class NotificationSettingsState(
    val enabled: Boolean = true,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
)

/**
 * Reads and writes the reminder settings — §11.3, §18.
 *
 * Scheduling itself is not done here. The work that posts a notification lives in the app module
 * (it needs `WorkManager` and the app's own workers), so this view model owns the *setting* and
 * hands the new value up through a callback; the app module is the single place that decides what
 * a setting change does to the queue.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<NotificationSettingsState> = preferences.userPreferences
        .map {
            NotificationSettingsState(
                enabled = it.notificationsEnabled,
                hour = it.notificationHour,
                minute = it.notificationMinute,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NotificationSettingsState(),
        )

    fun setEnabled(enabled: Boolean, onApplied: (NotificationSettingsState) -> Unit = {}) {
        viewModelScope.launch {
            preferences.setNotificationsEnabled(enabled)
            onApplied(state.value.copy(enabled = enabled))
        }
    }

    fun setTime(hour: Int, minute: Int, onApplied: (NotificationSettingsState) -> Unit = {}) {
        viewModelScope.launch {
            preferences.setNotificationTime(hour, minute)
            val stored = preferences.get()
            onApplied(
                NotificationSettingsState(
                    enabled = stored.notificationsEnabled,
                    hour = stored.notificationHour,
                    minute = stored.notificationMinute,
                ),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private const val DEFAULT_HOUR = 8
