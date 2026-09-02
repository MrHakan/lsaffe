package com.deckwatch.feature.settings.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the folder the officer picked for the §18 automatic weekly backup.
 *
 * ### Why a module-private `SharedPreferences` and not a DataStore key
 *
 * `UserPreferences` (core-datastore) holds the §18 *settings* — the things Settings shows and
 * "reset settings" clears. A persisted SAF tree URI is not one of them: it is a **capability grant**
 * from the system, paired with a `takePersistableUriPermission` that lives in the platform's own
 * permission table. Storing it beside the theme choice would mean a settings reset silently
 * revoking a grant the platform still holds, and a settings restore re-introducing a URI that was
 * released. There is no existing DataStore key for it, and adding one would put a
 * platform-lifetime value in a user-preferences store — so it lives here, next to the code that
 * takes and releases the permission, in `deckwatch_backup`.
 *
 * The URI is *validated on read*: a tree the user has since revoked, or a removed SD card, comes
 * back as null rather than as a URI that fails at 03:00 with nobody watching.
 */
@Singleton
class BackupFolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The chosen folder, or null when none is set or the grant has gone. */
    fun current(): Uri? {
        val stored = prefs().getString(KEY_TREE_URI, null)?.takeIf { it.isNotBlank() } ?: return null
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        val writable = runCatching {
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission } &&
                DocumentFile.fromTreeUri(context, uri)?.canWrite() == true
        }.getOrDefault(false)
        return if (writable) uri else null
    }

    /** The chosen folder's display name, for the settings row's subtitle. */
    fun displayName(): String? = current()
        ?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() }

    /**
     * Persist the tree the user picked with `ACTION_OPEN_DOCUMENT_TREE` and take the long-lived
     * read/write grant, so the weekly job can still write to it after a reboot.
     *
     * @return true when the grant was taken; false when the system refused it, in which case
     *   nothing is stored and the caller tells the user rather than scheduling a job that cannot run.
     */
    fun set(treeUri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val taken = runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.isSuccess
        if (taken) prefs().edit { putString(KEY_TREE_URI, treeUri.toString()) }
        return taken
    }

    /** Forget the folder and hand the grant back to the platform. */
    fun clear() {
        val stored = prefs().getString(KEY_TREE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (stored != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    stored,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs().edit { remove(KEY_TREE_URI) }
    }

    private fun prefs() = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "deckwatch_backup"
        const val KEY_TREE_URI = "auto_backup_tree_uri"
    }
}
