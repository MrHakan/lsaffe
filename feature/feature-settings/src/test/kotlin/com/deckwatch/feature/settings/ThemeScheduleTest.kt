package com.deckwatch.feature.settings

import com.deckwatch.core.datastore.UserPreferences
import com.deckwatch.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The 20:00–06:00 automatic theme window of §14 — see [ThemeSchedule] for the rule in full. */
class ThemeScheduleTest {

    @Test
    fun `night window runs from 20 to 06 inclusive of 20 exclusive of 06`() {
        assertThat(ThemeSchedule.isNightHour(19)).isFalse()
        assertThat(ThemeSchedule.isNightHour(20)).isTrue()
        assertThat(ThemeSchedule.isNightHour(23)).isTrue()
        assertThat(ThemeSchedule.isNightHour(0)).isTrue()
        assertThat(ThemeSchedule.isNightHour(5)).isTrue()
        assertThat(ThemeSchedule.isNightHour(6)).isFalse()
        assertThat(ThemeSchedule.isNightHour(12)).isFalse()
    }

    @Test
    fun `schedule off uses the chosen mode at every hour`() {
        for (mode in ThemeMode.entries) {
            val prefs = UserPreferences(themeMode = mode, themeFollowSchedule = false)
            for (hour in 0..23) {
                assertThat(ThemeSchedule.resolve(prefs, hour)).isEqualTo(mode)
            }
        }
    }

    @Test
    fun `schedule on gives day between 06 and 20 whatever the chosen mode`() {
        for (mode in ThemeMode.entries) {
            val prefs = UserPreferences(themeMode = mode, themeFollowSchedule = true)
            assertThat(ThemeSchedule.resolve(prefs, 6)).isEqualTo(ThemeMode.DAY)
            assertThat(ThemeSchedule.resolve(prefs, 13)).isEqualTo(ThemeMode.DAY)
            assertThat(ThemeSchedule.resolve(prefs, 19)).isEqualTo(ThemeMode.DAY)
        }
    }

    @Test
    fun `schedule on gives night after dark for day and night choices`() {
        val fromDay = UserPreferences(themeMode = ThemeMode.DAY, themeFollowSchedule = true)
        val fromNight = UserPreferences(themeMode = ThemeMode.NIGHT, themeFollowSchedule = true)
        assertThat(ThemeSchedule.resolve(fromDay, 21)).isEqualTo(ThemeMode.NIGHT)
        assertThat(ThemeSchedule.resolve(fromNight, 3)).isEqualTo(ThemeMode.NIGHT)
    }

    @Test
    fun `bridge stays bridge after dark`() {
        val prefs = UserPreferences(themeMode = ThemeMode.BRIDGE, themeFollowSchedule = true)
        assertThat(ThemeSchedule.resolve(prefs, 22)).isEqualTo(ThemeMode.BRIDGE)
        assertThat(ThemeSchedule.resolve(prefs, 4)).isEqualTo(ThemeMode.BRIDGE)
        // …but daylight is still Day: C7 makes the light theme the sunlight theme.
        assertThat(ThemeSchedule.resolve(prefs, 10)).isEqualTo(ThemeMode.DAY)
    }

    @Test
    fun `out of range hours normalise rather than throw`() {
        assertThat(ThemeSchedule.isNightHour(24)).isTrue()
        assertThat(ThemeSchedule.isNightHour(-1)).isTrue()
        assertThat(ThemeSchedule.isNightHour(30)).isFalse()
    }
}
