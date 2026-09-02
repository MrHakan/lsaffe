package com.deckwatch.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import com.deckwatch.core.model.AppLanguage
import java.util.Locale

/**
 * Applies the §18 **language** setting — English (default) or Turkish, C8.
 *
 * ### Why this and not `AppCompatDelegate.setApplicationLocales`
 *
 * The obvious call is `AppCompatDelegate.setApplicationLocales(...)`, and `androidx.appcompat` is
 * in the version catalogue. It was rejected: on API < 33 that API only re-applies the locale by
 * recreating **AppCompat** activities, and DeckWatch's single activity is a `ComponentActivity`
 * hosting Compose. Making it an `AppCompatActivity` would drag the whole AppCompat theme lineage
 * (`Theme.AppCompat.*`) into `themes.xml` — including the splash theme of §16 phase 11 — for one
 * string setting. That is a lot of surface for no benefit, since the platform already offers the
 * two halves of the job:
 *
 * * **API 33+** — the framework's per-app language, [LocaleManager.setApplicationLocales]. The
 *   system restarts the app's activities itself and the choice shows up in Android's own
 *   *Settings → Apps → DeckWatch → Language*, which is where a user will look for it.
 * * **API 26–32** — there is no per-app language, so the tag is remembered here and applied by
 *   [wrap], which the activity calls from `attachBaseContext`. The activity has to be recreated for
 *   the change to be visible, which the settings screen does; the settings screen also shows a
 *   restart hint, since a `Configuration` swap cannot reach a notification already posted or a
 *   worker already enqueued.
 *
 * The tag is mirrored into a tiny module-private `SharedPreferences` file rather than read from the
 * settings DataStore because `attachBaseContext` runs before any coroutine can, and blocking the
 * main thread on a DataStore read at activity creation is exactly the kind of cold-start cost
 * §17.3 budgets against. DataStore stays the source of truth; this file is a synchronous cache of
 * one string, written on every change.
 */
object AppLocale {

    private const val PREFS_NAME = "deckwatch_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    /** The BCP-47 tag DeckWatch uses for each supported UI language. */
    fun tagOf(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> "en"
        AppLanguage.TURKISH -> "tr"
    }

    /**
     * Record and apply [language].
     *
     * @return true when the platform applied it itself (API 33+) and the caller needs to do
     *   nothing; false when the caller must recreate the activity for the change to show.
     */
    fun apply(context: Context, language: AppLanguage): Boolean {
        val tag = tagOf(language)
        prefs(context).edit { putString(KEY_LANGUAGE_TAG, tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager != null) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
                return true
            }
        }
        return false
    }

    /** The stored tag, or null when the officer has never chosen a language. */
    fun storedTag(context: Context): String? =
        prefs(context).getString(KEY_LANGUAGE_TAG, null)?.takeIf { it.isNotBlank() }

    /**
     * Wrap a base context in the chosen locale — call from `Activity.attachBaseContext`.
     *
     * A no-op on API 33+, where the framework has already applied the per-app language and
     * overriding it here would fight the system setting.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isBlank()) return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
