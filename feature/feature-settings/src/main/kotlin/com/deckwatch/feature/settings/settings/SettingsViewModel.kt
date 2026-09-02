package com.deckwatch.feature.settings.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckwatch.core.datastore.UserPreferences
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The §18 settings screen's state and every write behind it.
 *
 * ### Shape
 *
 * The state *is* [UserPreferences]: the screen renders the stored settings and nothing else, so
 * introducing a parallel UI model would only create a second place for a control to disagree with
 * the value it is supposed to show. Every setter writes straight through to
 * [UserPreferencesRepository] and the new value comes back through the flow — a control is never
 * optimistically updated, so what is on screen is always what is on disk. That is what makes a
 * half-completed write visible instead of invisible.
 *
 * ### What is deliberately *not* here
 *
 * * **Re-arming the daily worker.** `DueRecomputeWorker` reads `notificationsEnabled` itself before
 *   posting and recomputes due dates either way (§11.2 wants the recomputation whatever the
 *   notification setting is), so toggling the reminder needs no scheduling change. The localised
 *   notification strings are refreshed by the app's startup coordinator on the next cold start,
 *   with `ExistingPeriodicWorkPolicy.UPDATE`, which is also what picks up a language change.
 * * **Applying the language.** That needs a `Context` and an activity to recreate, so it stays in
 *   the composable (see `AppLocale`); this class only records the choice.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<UserPreferences> = preferences.userPreferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UserPreferences(),
    )

    fun setThemeMode(mode: ThemeMode) = write { setThemeMode(mode) }

    fun setThemeFollowSchedule(enabled: Boolean) = write { setThemeFollowSchedule(enabled) }

    fun setLanguage(language: AppLanguage) = write { setLanguage(language) }

    fun setDensity(density: ListDensity) = write { setDensity(density) }

    fun setDueLeadTimeDays(days: Int) = write { setDueLeadTimeDays(days) }

    fun setNotificationTime(hour: Int, minute: Int) = write { setNotificationTime(hour, minute) }

    fun setNotificationsEnabled(enabled: Boolean) = write { setNotificationsEnabled(enabled) }

    fun setDefaultFlag(flag: FlagState) = write { setDefaultFlag(flag) }

    fun setIsoAngleDeg(degrees: Float) = write { setIsoAngleDeg(degrees) }

    fun setGridSnapEnabled(enabled: Boolean) = write { setGridSnapEnabled(enabled) }

    fun setTagNumberFormat(format: String) = write { setTagNumberFormat(format) }

    fun setPhotoQuality(quality: String) = write { setPhotoQuality(quality) }

    fun setMetricUnits(metric: Boolean) = write { setMetricUnits(metric) }

    fun setFirstDayOfWeek(day: Int) = write { setFirstDayOfWeek(day) }

    private fun write(block: suspend UserPreferencesRepository.() -> Unit) {
        viewModelScope.launch { preferences.block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The lead-time slider's range — §18 asks for a due lead time; 7–90 days is the useful span. */
val DUE_LEAD_TIME_RANGE: IntRange = 7..90

/** The days offered by "week starts on": ISO-8601 day numbers for Monday, Saturday and Sunday. */
val FIRST_DAY_CHOICES: List<Int> = listOf(1, 6, 7)

/**
 * Render [format] with sample values so the officer sees the tag they will get, live — §18's "tag
 * auto-numbering format" and DESIGN_OVERHAUL's live-consequence rule.
 *
 * The placeholders are the ones named by `UserPreferences.DEFAULT_TAG_NUMBER_FORMAT`
 * (`{PREFIX}-{DECK}-{NNN}`). An unknown placeholder is left alone rather than blanked, so a typo
 * shows up as itself instead of vanishing.
 *
 * Note the honest gap: `feature-equipment`'s `TagSuggestion` currently hard-codes
 * `PREFIX-DECK-NN`. The setting is stored (§18 requires it) and previewed here, but the add-
 * equipment flow will not follow a custom format until that module reads it. Recorded in the
 * hand-off notes rather than hidden behind a control that appears to do something.
 */
fun tagFormatExample(format: String): String = format
    .replace("{PREFIX}", "FE")
    .replace("{DECK}", "UD")
    .replace("{NNN}", "003")
    .replace("{NN}", "03")
    .replace("{N}", "3")
    .ifBlank { "FE-UD-003" }
