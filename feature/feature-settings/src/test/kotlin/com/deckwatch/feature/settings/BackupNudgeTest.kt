package com.deckwatch.feature.settings

import com.deckwatch.core.datastore.UserPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** §18's "prompt for a backup on the 30th day of use". */
class BackupNudgeTest {

    private val firstRun = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `no prompt before the thirtieth day`() {
        val prefs = UserPreferences(firstRunAt = firstRun, lastBackupAt = null)
        assertThat(BackupNudge.shouldPrompt(prefs, firstRun + 29 * day)).isFalse()
    }

    @Test
    fun `prompts on the thirtieth day`() {
        val prefs = UserPreferences(firstRunAt = firstRun, lastBackupAt = null)
        assertThat(BackupNudge.shouldPrompt(prefs, firstRun + 30 * day)).isTrue()
    }

    @Test
    fun `keeps prompting after the thirtieth day while no backup exists`() {
        val prefs = UserPreferences(firstRunAt = firstRun, lastBackupAt = null)
        assertThat(BackupNudge.shouldPrompt(prefs, firstRun + 400 * day)).isTrue()
    }

    @Test
    fun `one backup silences it permanently`() {
        val prefs = UserPreferences(firstRunAt = firstRun, lastBackupAt = firstRun + day)
        assertThat(BackupNudge.shouldPrompt(prefs, firstRun + 900 * day)).isFalse()
    }

    @Test
    fun `unknown first run never prompts`() {
        // firstRunAt is 0 until markFirstRun lands. Treating 0 as 1970 would fire the banner on the
        // very first launch, which is the opposite of what §18 asks for.
        val prefs = UserPreferences(firstRunAt = 0L, lastBackupAt = null)
        assertThat(BackupNudge.shouldPrompt(prefs, firstRun)).isFalse()
    }

    @Test
    fun `a clock that has gone backwards does not prompt`() {
        val prefs = UserPreferences(firstRunAt = firstRun, lastBackupAt = null)
        assertThat(BackupNudge.shouldPrompt(prefs, firstRun - 10 * day)).isFalse()
    }
}
