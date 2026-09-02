package com.deckwatch.core.datastore

import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode

/**
 * Every user setting the app keeps — MASTER_PROMPT §18, plus the rendering and formatting choices
 * of §7.2 and §14.
 *
 * The defaults here are the app's out-of-the-box behaviour and are all deliberate:
 * - [themeMode] is `DAY` because the high-contrast light theme is the default for sunlight on deck
 *   (C7). [themeFollowSchedule] is off until the user asks for automatic switching (§14).
 * - [language] is English (C8); the officer switches to Turkish explicitly.
 * - [density] is compact (§14 "compact by default").
 * - [dueLeadTimeDays] 30 is the `DUE_SOON` lead time of §11.1 step 4.
 * - [notificationHour]/[notificationMinute] make the daily digest 08:00 (§11.3).
 * - [isoAngleDeg] 30° is the default dimetric projection angle (§7.2).
 * - [metricUnits] true — metric is the default per §18.
 * - [firstDayOfWeek] 1 is Monday, matching `java.time.DayOfWeek.MONDAY.value`.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DAY,
    val themeFollowSchedule: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val density: ListDensity = ListDensity.COMPACT,
    /** How many days ahead an item counts as `DUE_SOON` — §11.1 step 4. */
    val dueLeadTimeDays: Int = DEFAULT_DUE_LEAD_TIME_DAYS,
    /** Hour of the daily digest notification, 0..23 — §11.3. */
    val notificationHour: Int = DEFAULT_NOTIFICATION_HOUR,
    val notificationMinute: Int = DEFAULT_NOTIFICATION_MINUTE,
    /** The app stays fully usable with notifications off or the permission denied — §11.3. */
    val notificationsEnabled: Boolean = true,
    /** Pre-selected flag when creating a vessel. */
    val defaultFlag: FlagState = FlagState.OTHER,
    /** Isometric projection angle in degrees, 0 (flat plan) to 35 — §7.2. */
    val isoAngleDeg: Float = DEFAULT_ISO_ANGLE_DEG,
    /** Off by default: free placement first, snapping is opt-in — §7.2. */
    val gridSnapEnabled: Boolean = false,
    /** Tag auto-numbering pattern — §7.5 step 3, §18. */
    val tagNumberFormat: String = DEFAULT_TAG_NUMBER_FORMAT,
    /** One of [PhotoQuality]. Kept as a string so a future tier needs no schema change. */
    val photoQuality: String = PhotoQuality.MEDIUM,
    val metricUnits: Boolean = true,
    /** ISO-8601 day number: 1 = Monday … 7 = Sunday. */
    val firstDayOfWeek: Int = DEFAULT_FIRST_DAY_OF_WEEK,
    /** null until the officer has created or selected a vessel. */
    val activeVesselId: String? = null,
    val onboardingDone: Boolean = false,
    /** The §17.6 disclaimer must be accepted on first run. */
    val disclaimerAccepted: Boolean = false,
    /**
     * Hides the permanent disclaimer strip at the foot of the Notes tab once the officer has
     * dismissed it. The disclaimer itself is not removed from the app — the first-run banner and
     * More → About still carry it in full — only the strip that repeats it on every screen.
     */
    val notesFooterDismissed: Boolean = false,
    /** Epoch-millis of the last successful backup; null == never — drives the §18 day-30 prompt. */
    val lastBackupAt: Long? = null,
    /** Epoch-millis of first launch; 0 until [UserPreferencesRepository.markFirstRun] has run. */
    val firstRunAt: Long = 0L,
    /**
     * Version of the bundled reference content already imported into the database; 0 until the
     * first import. A newer bundle re-seeds the bundled rows and leaves the user's own alone
     * (§19).
     */
    val seededContentVersion: Int = 0,
)

/** Allowed values of [UserPreferences.photoQuality] — the export tiers of §13.2. */
object PhotoQuality {
    const val LOW: String = "LOW"
    const val MEDIUM: String = "MEDIUM"
    const val HIGH: String = "HIGH"

    val ALL: List<String> = listOf(LOW, MEDIUM, HIGH)
}

internal const val DEFAULT_DUE_LEAD_TIME_DAYS = 30
internal const val DEFAULT_NOTIFICATION_HOUR = 8
internal const val DEFAULT_NOTIFICATION_MINUTE = 0
internal const val DEFAULT_ISO_ANGLE_DEG = 30f
internal const val DEFAULT_FIRST_DAY_OF_WEEK = 1
internal const val DEFAULT_TAG_NUMBER_FORMAT = "{PREFIX}-{DECK}-{NNN}"

/** The isometric angle is clamped to this range: past 35° the deck stack stops reading — §7.2. */
val ISO_ANGLE_RANGE: ClosedFloatingPointRange<Float> = 0f..35f
