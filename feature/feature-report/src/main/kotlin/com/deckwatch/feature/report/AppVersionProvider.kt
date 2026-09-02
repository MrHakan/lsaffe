package com.deckwatch.feature.report

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The installed app version, for the report header and the payload's `appVersion` (§13.4).
 *
 * Read from the package manager rather than a `BuildConfig` constant so that this module needs no
 * dependency on `:app` and the value is whatever is actually installed on the device the report
 * came off — which is the version a reader of the file cares about.
 */
@Singleton
class AppVersionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** e.g. `1.4.0 (140)`. Never throws — an unnamed version is better than a failed export. */
    fun versionName(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val name = info.versionName.orEmpty()
        if (name.isBlank()) code.toString() else "$name ($code)"
    }.getOrDefault(UNKNOWN)

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
