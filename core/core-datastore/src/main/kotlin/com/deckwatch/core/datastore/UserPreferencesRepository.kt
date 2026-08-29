package com.deckwatch.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deckwatch.core.model.AppLanguage
import com.deckwatch.core.model.FlagState
import com.deckwatch.core.model.ListDensity
import com.deckwatch.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Reads and writes the settings of MASTER_PROMPT §18 in a Preferences DataStore named `settings`.
 *
 * Two behaviours are load-bearing:
 * - An [IOException] while reading (a truncated file on a phone that lost power mid-write) emits
 *   empty preferences rather than propagating, so the app falls back to defaults instead of
 *   crashing at startup. Any other failure is still thrown — it is a bug, not a bad disk.
 * - Unknown enum names decode to the default instead of throwing. A settings file written by a
 *   newer build must not brick an older one; the same tolerance is what §13.5 requires of import.
 */
class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(Preferences::toUserPreferences)

    /** A one-shot read, for callers that cannot collect (WorkManager, migrations, exporters). */
    suspend fun get(): UserPreferences = userPreferences.first()

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME_MODE, mode.name)

    suspend fun setThemeFollowSchedule(enabled: Boolean) = put(Keys.THEME_FOLLOW_SCHEDULE, enabled)

    suspend fun setLanguage(language: AppLanguage) = put(Keys.LANGUAGE, language.name)

    suspend fun setDensity(density: ListDensity) = put(Keys.DENSITY, density.name)

    /** Negative lead times are meaningless; the value is coerced to at least 0. */
    suspend fun setDueLeadTimeDays(days: Int) = put(Keys.DUE_LEAD_TIME_DAYS, days.coerceAtLeast(0))

    suspend fun setNotificationTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_HOUR] = hour.coerceIn(HOUR_RANGE)
            prefs[Keys.NOTIFICATION_MINUTE] = minute.coerceIn(MINUTE_RANGE)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = put(Keys.NOTIFICATIONS_ENABLED, enabled)

    suspend fun setDefaultFlag(flag: FlagState) = put(Keys.DEFAULT_FLAG, flag.name)

    /** Clamped to [ISO_ANGLE_RANGE]; 0 collapses the isometric view into the flat plan (§7.2). */
    suspend fun setIsoAngleDeg(degrees: Float) =
        put(Keys.ISO_ANGLE_DEG, degrees.coerceIn(ISO_ANGLE_RANGE))

    suspend fun setGridSnapEnabled(enabled: Boolean) = put(Keys.GRID_SNAP_ENABLED, enabled)

    /** A blank format would produce untraceable tags, so it falls back to the default. */
    suspend fun setTagNumberFormat(format: String) =
        put(Keys.TAG_NUMBER_FORMAT, format.ifBlank { DEFAULT_TAG_NUMBER_FORMAT })

    /** [quality] must be one of [PhotoQuality]; anything else falls back to `MEDIUM`. */
    suspend fun setPhotoQuality(quality: String) = put(
        Keys.PHOTO_QUALITY,
        if (quality in PhotoQuality.ALL) quality else PhotoQuality.MEDIUM,
    )

    suspend fun setMetricUnits(metric: Boolean) = put(Keys.METRIC_UNITS, metric)

    /** ISO-8601 day number, 1 (Monday) to 7 (Sunday). */
    suspend fun setFirstDayOfWeek(day: Int) =
        put(Keys.FIRST_DAY_OF_WEEK, day.coerceIn(DAY_OF_WEEK_RANGE))

    /** Passing null clears the selection — the app then has no active vessel. */
    suspend fun setActiveVesselId(vesselId: String?) {
        dataStore.edit { prefs ->
            if (vesselId.isNullOrBlank()) {
                prefs.remove(Keys.ACTIVE_VESSEL_ID)
            } else {
                prefs[Keys.ACTIVE_VESSEL_ID] = vesselId
            }
        }
    }

    suspend fun setOnboardingDone(done: Boolean) = put(Keys.ONBOARDING_DONE, done)

    suspend fun setDisclaimerAccepted(accepted: Boolean) = put(Keys.DISCLAIMER_ACCEPTED, accepted)

    /** Epoch-millis. Passing null clears it, which makes the app treat the vessel as never backed up. */
    suspend fun setLastBackupAt(atMillis: Long?) {
        dataStore.edit { prefs ->
            if (atMillis == null) prefs.remove(Keys.LAST_BACKUP_AT) else prefs[Keys.LAST_BACKUP_AT] = atMillis
        }
    }

    suspend fun setFirstRunAt(atMillis: Long) = put(Keys.FIRST_RUN_AT, atMillis)

    /**
     * Records first launch exactly once. Later calls are no-ops, so the §18 "prompt for a backup
     * on the 30th day of use" counter is anchored to the real first run and never resets.
     */
    suspend fun markFirstRun(atMillis: Long) {
        dataStore.edit { prefs ->
            if (prefs[Keys.FIRST_RUN_AT] == null) prefs[Keys.FIRST_RUN_AT] = atMillis
        }
    }

    /** Wipes every stored setting. Used by "reset settings" and by tests. */
    suspend fun clear() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private companion object {
        val HOUR_RANGE = 0..23
        val MINUTE_RANGE = 0..59
        val DAY_OF_WEEK_RANGE = 1..7
    }
}

/** Every preference key in the `settings` store, in one place so a name cannot be typed twice. */
internal object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_FOLLOW_SCHEDULE = booleanPreferencesKey("theme_follow_schedule")
    val LANGUAGE = stringPreferencesKey("language")
    val DENSITY = stringPreferencesKey("density")
    val DUE_LEAD_TIME_DAYS = intPreferencesKey("due_lead_time_days")
    val NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
    val NOTIFICATION_MINUTE = intPreferencesKey("notification_minute")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val DEFAULT_FLAG = stringPreferencesKey("default_flag")
    val ISO_ANGLE_DEG = floatPreferencesKey("iso_angle_deg")
    val GRID_SNAP_ENABLED = booleanPreferencesKey("grid_snap_enabled")
    val TAG_NUMBER_FORMAT = stringPreferencesKey("tag_number_format")
    val PHOTO_QUALITY = stringPreferencesKey("photo_quality")
    val METRIC_UNITS = booleanPreferencesKey("metric_units")
    val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
    val ACTIVE_VESSEL_ID = stringPreferencesKey("active_vessel_id")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    val FIRST_RUN_AT = longPreferencesKey("first_run_at")
}

/** Decodes a stored preferences snapshot, falling back to the [UserPreferences] defaults. */
internal fun Preferences.toUserPreferences(): UserPreferences {
    val defaults = UserPreferences()
    return UserPreferences(
        themeMode = this[Keys.THEME_MODE].asEnum(defaults.themeMode),
        themeFollowSchedule = this[Keys.THEME_FOLLOW_SCHEDULE] ?: defaults.themeFollowSchedule,
        language = this[Keys.LANGUAGE].asEnum(defaults.language),
        density = this[Keys.DENSITY].asEnum(defaults.density),
        dueLeadTimeDays = this[Keys.DUE_LEAD_TIME_DAYS] ?: defaults.dueLeadTimeDays,
        notificationHour = this[Keys.NOTIFICATION_HOUR] ?: defaults.notificationHour,
        notificationMinute = this[Keys.NOTIFICATION_MINUTE] ?: defaults.notificationMinute,
        notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: defaults.notificationsEnabled,
        defaultFlag = this[Keys.DEFAULT_FLAG].asEnum(defaults.defaultFlag),
        isoAngleDeg = (this[Keys.ISO_ANGLE_DEG] ?: defaults.isoAngleDeg).coerceIn(ISO_ANGLE_RANGE),
        gridSnapEnabled = this[Keys.GRID_SNAP_ENABLED] ?: defaults.gridSnapEnabled,
        tagNumberFormat = this[Keys.TAG_NUMBER_FORMAT] ?: defaults.tagNumberFormat,
        photoQuality = this[Keys.PHOTO_QUALITY]?.takeIf { it in PhotoQuality.ALL }
            ?: defaults.photoQuality,
        metricUnits = this[Keys.METRIC_UNITS] ?: defaults.metricUnits,
        firstDayOfWeek = this[Keys.FIRST_DAY_OF_WEEK] ?: defaults.firstDayOfWeek,
        activeVesselId = this[Keys.ACTIVE_VESSEL_ID]?.takeIf(String::isNotBlank),
        onboardingDone = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
        disclaimerAccepted = this[Keys.DISCLAIMER_ACCEPTED] ?: defaults.disclaimerAccepted,
        lastBackupAt = this[Keys.LAST_BACKUP_AT],
        firstRunAt = this[Keys.FIRST_RUN_AT] ?: defaults.firstRunAt,
    )
}

/**
 * Resolves a stored enum name without throwing. A name this build does not know (an older app
 * reading a newer settings file) yields [default] rather than an exception at startup.
 */
private inline fun <reified T : Enum<T>> String?.asEnum(default: T): T =
    this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
