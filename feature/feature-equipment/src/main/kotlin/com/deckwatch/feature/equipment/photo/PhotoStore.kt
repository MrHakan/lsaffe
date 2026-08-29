package com.deckwatch.feature.equipment.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Where equipment photos live and how they get there — MASTER_PROMPT §7.6.
 *
 * Photos are app-private files under `filesDir/photos/<equipmentId>/`, never the shared gallery:
 * an inspection record must not depend on a picture the officer can delete from Photos, and a
 * vessel's LSA survey is not something to scatter across a personal phone's media store. The
 * camera app reaches exactly one file at a time through the `${applicationId}.fileprovider`
 * authority declared in this module's manifest.
 *
 * Nothing here needs the `CAMERA` permission: the capture runs in whatever camera app the phone
 * has, under a per-file write grant. Declaring `CAMERA` would add a runtime prompt and, worse,
 * would make the app disappear from the device list for phones without a camera.
 */
object PhotoStore {

    /** Directory holding one item's photos; created on demand. */
    fun directoryFor(context: Context, equipmentId: String): File =
        File(File(context.filesDir, PHOTOS_DIR), equipmentId.sanitised()).apply { mkdirs() }

    /**
     * A fresh, empty file for the next capture. The name carries the capture time so the files
     * sort chronologically in a backup, and collisions within the same millisecond are impossible
     * because the file is created here before the camera ever sees it.
     */
    fun newPhotoFile(context: Context, equipmentId: String, atMillis: Long): File {
        val dir = directoryFor(context, equipmentId)
        var candidate = File(dir, "%d.jpg".format(Locale.ROOT, atMillis))
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "%d-%d.jpg".format(Locale.ROOT, atMillis, suffix))
            suffix++
        }
        return candidate
    }

    /** The content URI to hand the camera app for [file]. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /**
     * Resolves a stored URI back to the file on disk, or null if it does not belong to this app's
     * photo directory. Stored records outlive installs and imports, so a URI that no longer maps
     * to a real file is normal, not a bug.
     *
     * The provider's path is `<root name>/<equipment id>/<file>`, so the first segment is dropped
     * and the rest is re-joined under the photo directory — using only the last segment would
     * lose the per-item folder and resolve two items' photos to the same name.
     */
    fun fileFor(context: Context, uri: String): File? {
        val segments = Uri.parse(uri).pathSegments.orEmpty()
        if (segments.size < 2 || segments.first() != ROOT_NAME) return null
        val root = File(context.filesDir, PHOTOS_DIR)
        val file = File(root, segments.drop(1).joinToString(File.separator))
        return file.takeIf { it.exists() && it.canonicalPath.startsWith(root.canonicalPath) }
    }

    /**
     * Decodes a stored photo down to roughly [maxEdge] pixels on its longest side. Thumbnails on a
     * scrolling sheet must never hold a full-resolution bitmap — a 12 MP capture is ~48 MB decoded,
     * and a handful of them would take the app down on a mid-range phone.
     */
    fun decodeThumbnail(context: Context, uri: String, maxEdge: Int): Bitmap? {
        val file = fileFor(context, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null

        var sampleSize = 1
        while (longestEdge / sampleSize > maxEdge) sampleSize *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    /** Deletes the file behind a stored URI. A URI with no file left is treated as already gone. */
    fun delete(context: Context, uri: String): Boolean = fileFor(context, uri)?.delete() ?: true

    /**
     * Ids come from the id factory and are already filesystem-safe, but a path separator arriving
     * from an imported record must never escape the photo directory.
     */
    private fun String.sanitised(): String = filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .ifBlank { "unknown" }

    private const val PHOTOS_DIR = "photos"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /** The `name` of the `<files-path>` in `res/xml/file_paths.xml`; the first URI path segment. */
    const val ROOT_NAME: String = "equipment_photos"

    /** Authority of the provider that serves [uriFor]; derived from the installed applicationId. */
    fun authority(context: Context): String = "${context.packageName}$FILE_PROVIDER_SUFFIX"

    /** Longest edge of a photo thumbnail, in pixels. */
    const val THUMBNAIL_MAX_EDGE: Int = 512
}
