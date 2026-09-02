package com.deckwatch.feature.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.deckwatch.core.common.DefaultDispatcherProvider
import com.deckwatch.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes reports to the cache and hands them out — §13.6.
 *
 * Files land in `cacheDir/exports`, which is the directory declared in this module's
 * `res/xml/file_paths.xml` and exposed through the `FileProvider` declared in this module's
 * `AndroidManifest.xml` with the authority `${'$'}{applicationId}.reports`. A library manifest
 * merges into the host app, so the app module needs no change and the authority follows whatever
 * application id the app is built with.
 *
 * Cache is the right home: an exported report is a *copy*, the operating system may reclaim it,
 * and nothing in the app depends on it still being there tomorrow. The path of the most recent one
 * is remembered so "Share last report" works after the screen is gone (§13.6) — in
 * `SharedPreferences`, so it also survives process death (§17.4).
 */
@Singleton
class ReportFileStore(
    private val context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(Dispatchers.Main),
) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context, DefaultDispatcherProvider(Dispatchers.Main))

    /** `cacheDir/exports`, created on demand. */
    val exportsDir: File
        get() = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }

    /** The `FileProvider` authority declared in this module's manifest. */
    val authority: String get() = "${context.packageName}$AUTHORITY_SUFFIX"

    /** Write [content] as [fileName] into the exports cache and return the file. */
    suspend fun write(fileName: String, content: String): File = withContext(dispatchers.io) {
        val file = File(exportsDir, fileName)
        file.writeText(content)
        rememberLast(file)
        file
    }

    /** A `content://` URI for [file], safe to hand to another app. */
    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    /**
     * The `ACTION_SEND` chooser of §13.6, wrapped so the receiving app gets read permission.
     *
     * The MIME type is the real one — `text/html` for a report, `application/json` for a raw
     * payload — because that is what makes a mail client show it as an attachment and a phone
     * browser open it instead of downloading it blind.
     */
    fun shareIntent(file: File, mimeType: String = MIME_HTML, subject: String = file.name): Intent {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TITLE, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, subject).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Copy [file] to a SAF destination the user picked with `ACTION_CREATE_DOCUMENT` — the
     * "Save to Downloads" of §13.6.
     *
     * @return true when the bytes were written. False rather than an exception: a user who
     *   cancelled, or picked a location that has since gone away, gets a snackbar, not a crash.
     */
    suspend fun copyToDocument(file: File, destination: Uri): Boolean = withContext(dispatchers.io) {
        runCatching {
            val document = DocumentFile.fromSingleUri(context, destination)
            if (document != null && !document.canWrite()) return@runCatching false
            context.contentResolver.openOutputStream(destination)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)
    }

    /** Read a user-picked file as text, for import. Null when it cannot be read at all — §17.4. */
    suspend fun readText(source: Uri): String? = withContext(dispatchers.io) {
        runCatching {
            context.contentResolver.openInputStream(source)?.use { it.reader().readText() }
        }.getOrNull()
    }

    /** The display name SAF reports for [source], for the import screen's header. */
    suspend fun displayName(source: Uri): String? = withContext(dispatchers.io) {
        runCatching { DocumentFile.fromSingleUri(context, source)?.name }.getOrNull()
    }

    /** The most recently written report, or null when there is none or it has been evicted. */
    fun lastReport(): File? = prefs().getString(KEY_LAST_REPORT, null)
        ?.let(::File)
        ?.takeIf { it.isFile }

    private fun rememberLast(file: File) {
        prefs().edit().putString(KEY_LAST_REPORT, file.absolutePath).apply()
    }

    /**
     * Drop exports older than [maxAgeMillis]. The cache is the OS's to reclaim, but an app that
     * leaves 40 MB of yesterday's PSC packs lying about deserves the storage complaint it gets.
     */
    suspend fun prune(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS, nowMillis: Long = System.currentTimeMillis()) {
        withContext(dispatchers.io) {
            runCatching {
                exportsDir.listFiles()?.forEach { file ->
                    if (nowMillis - file.lastModified() > maxAgeMillis) file.delete()
                }
            }
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val MIME_HTML: String = "text/html"
        const val MIME_JSON: String = "application/json"
        const val MIME_CSV: String = "text/csv"

        /** Must match `res/xml/file_paths.xml`. */
        const val EXPORTS_DIR: String = "exports"

        /** Must match the authority in this module's `AndroidManifest.xml`. */
        const val AUTHORITY_SUFFIX: String = ".reports"

        private const val PREFS = "deckwatch_reports"
        private const val KEY_LAST_REPORT = "last_report_path"
        private const val DEFAULT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
