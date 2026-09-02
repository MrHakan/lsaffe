package com.deckwatch.feature.settings

import com.deckwatch.core.datastore.UserPreferences

/**
 * The "data export on uninstall" warning of MASTER_PROMPT §18: *if the user has never taken a
 * backup, prompt for one on the 30th day of use*.
 *
 * Three conditions, all of them necessary:
 *
 * 1. **No backup has ever been taken** — `lastBackupAt == null`. Taking one backup silences the
 *    banner permanently; §18 asks for a prompt, not a nag.
 * 2. **First run is known** — `firstRunAt > 0`. The field is 0 until `markFirstRun` has landed, and
 *    an unknown first run is 1970, which would fire the banner on the very first launch.
 * 3. **Thirty days have elapsed** since first run.
 *
 * The banner itself is dismissible for the current session (the More screen holds that in
 * `rememberSaveable`); it is not persisted, so it comes back on the next cold start and stops for
 * good the moment a backup succeeds. That is the honest reading of "prompt on the 30th day": a
 * dismissal that outlived the risk would defeat the point.
 *
 * Pure, so `BackupNudgeTest` can drive it with a fixed clock.
 */
object BackupNudge {

    /** §18's thirtieth day of use. */
    const val NUDGE_AFTER_DAYS: Int = 30

    private const val MILLIS_PER_DAY = 86_400_000L

    /** Milliseconds of use after which an un-backed-up install is prompted. */
    const val NUDGE_AFTER_MILLIS: Long = NUDGE_AFTER_DAYS * MILLIS_PER_DAY

    fun shouldPrompt(preferences: UserPreferences, nowMillis: Long): Boolean =
        preferences.lastBackupAt == null &&
            preferences.firstRunAt > 0L &&
            nowMillis - preferences.firstRunAt >= NUDGE_AFTER_MILLIS
}
