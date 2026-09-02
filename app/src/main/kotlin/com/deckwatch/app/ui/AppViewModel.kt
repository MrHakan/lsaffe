package com.deckwatch.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.app.AppStartup
import com.deckwatch.core.datastore.UserPreferences
import com.deckwatch.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app shell's own state: the settings that decide the theme and the density, whether onboarding
 * is still owed, and the one-shot cold-start work.
 *
 * ### Why `preferences` is nullable
 *
 * Null means "DataStore has not answered yet". The splash screen is held on screen while it is null
 * (see `MainActivity`), which is what stops the app painting one frame of the Day theme before
 * flipping to Night, and one frame of the tab UI before flipping to onboarding. §17.3 budgets 1.5 s
 * for a cold start and a Preferences DataStore read is a few milliseconds, so this costs nothing
 * and removes the whole class of first-frame flicker.
 *
 * ### Why the onboarding gate reads from here and not from memory
 *
 * `onboardingDone` comes off the DataStore flow every time. A process death during onboarding
 * therefore resumes onboarding, and completing it in one process is visible in the next — §17.4.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val appStartup: AppStartup,
) : ViewModel() {

    private val readyState = MutableStateFlow(false)

    /** True once the first settings snapshot has arrived and the splash may be dismissed. */
    val ready: StateFlow<Boolean> = readyState.asStateFlow()

    val preferences: StateFlow<UserPreferences?> = preferencesRepository.userPreferences
        .onEach { readyState.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /**
     * The Notes tab's first-entry disclaimer banner was acknowledged — §8.5, §17.6.
     *
     * The tab holds the "seen" flag in `rememberSaveable` only; recording it here moves it to
     * DataStore, so acknowledging it once is enough and it is the same flag onboarding writes.
     */
    fun onDisclaimerAccepted() {
        viewModelScope.launch { preferencesRepository.setDisclaimerAccepted(true) }
    }

    init {
        viewModelScope.launch { appStartup.start() }
        // §11.2: recompute due state whenever the officer switches ship. Tied to the shell's
        // lifetime, so it stops with the UI rather than running for the life of the process.
        viewModelScope.launch { appStartup.observeActiveVessel() }
        // §11.3: the settings screen only writes the preference; re-arming the queue is this
        // module's job, because the workers are here.
        viewModelScope.launch { appStartup.observeReminderSettings() }
    }
}
