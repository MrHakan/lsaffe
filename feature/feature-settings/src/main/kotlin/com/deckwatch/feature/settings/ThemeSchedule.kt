package com.deckwatch.feature.settings

import com.deckwatch.core.datastore.UserPreferences
import com.deckwatch.core.model.ThemeMode

/**
 * The optional automatic theme schedule of MASTER_PROMPT §14 ("the theme … follows an optional
 * automatic schedule").
 *
 * ### The rule, in full
 *
 * The window is fixed at **20:00–06:00 local**. It is deliberately not user-configurable: §18's
 * settings list names "theme + schedule", not a pair of times, and one fixed window that everybody
 * understands beats two more pickers on a screen that already has thirteen controls. 20:00 is
 * after sunset the year round in the latitudes merchant shipping actually works, and 06:00 is
 * before the 08:00 morning digest, so the officer never reads the digest on a red screen.
 *
 * When [UserPreferences.themeFollowSchedule] is on, [UserPreferences.themeMode] stops meaning
 * "the theme" and starts meaning "**the night theme**":
 *
 * | Stored mode | 06:00–20:00 | 20:00–06:00 |
 * |---|---|---|
 * | DAY | Day | Night |
 * | NIGHT | Day | Night |
 * | BRIDGE | Day | **Bridge** |
 *
 * That is the behaviour the two night themes are for. An officer who has chosen Bridge has chosen
 * it *for the bridge at night*; switching them to plain Night at 20:00 would throw away the only
 * setting that matters to them. An officer who has chosen Day or Night gets true dark after dark.
 * Daytime is always Day, because C7 makes the high-contrast light theme the sunlight theme and no
 * one wants OLED black on deck at noon.
 *
 * With the schedule off, the stored mode is used exactly as chosen, at every hour.
 *
 * Pure and hour-based so it is unit-testable without a clock — see `ThemeScheduleTest`.
 */
object ThemeSchedule {

    /** First hour of the night window, inclusive. */
    const val NIGHT_STARTS_HOUR: Int = 20

    /** First hour of the day window, inclusive — i.e. the night window ends at 06:00. */
    const val NIGHT_ENDS_HOUR: Int = 6

    /** True when [hour] (0..23, local) falls inside the 20:00–06:00 night window. */
    fun isNightHour(hour: Int): Boolean {
        val normalised = ((hour % HOURS_PER_DAY) + HOURS_PER_DAY) % HOURS_PER_DAY
        return normalised >= NIGHT_STARTS_HOUR || normalised < NIGHT_ENDS_HOUR
    }

    /** The theme to render, given the stored settings and the local hour of day. */
    fun resolve(preferences: UserPreferences, hourOfDay: Int): ThemeMode = when {
        !preferences.themeFollowSchedule -> preferences.themeMode
        !isNightHour(hourOfDay) -> ThemeMode.DAY
        preferences.themeMode == ThemeMode.BRIDGE -> ThemeMode.BRIDGE
        else -> ThemeMode.NIGHT
    }

    private const val HOURS_PER_DAY = 24
}
