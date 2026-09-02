package com.deckwatch.feature.settings.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The `.dwbackup` container of MASTER_PROMPT §18 — *"the JSON payload + photos in a zip"*.
 *
 * ### Layout
 *
 * ```
 * manifest.json      BackupManifest — what this file is, and where each photo went
 * payload.json       the §13.2 DeckWatchExportPayload, verbatim
 * photos/0001.jpg    one entry per distinct photo URI referenced by the payload
 * photos/0002.jpg
 * …
 * ```
 *
 * ### One payload model, two wrappers
 *
 * **The `.dwbackup` and the §13.2 HTML report share the model.** `payload.json` is exactly the
 * bytes `feature-report`'s `PayloadAssembler` produces and `PayloadParser`/`ImportMerger` consume —
 * the same `DeckWatchExportPayload`, the same `schemaVersion`, the same merge rules. The two
 * formats differ only in what is wrapped around that JSON: the report wraps it in a readable HTML
 * document with `data:` photos inline (small enough to send over WhatsApp, §13.1), the backup wraps
 * it in a zip with the photos as real files (nothing is re-encoded, so a restore returns the
 * originals). Writing a second serialiser here would have guaranteed the two drift apart, and a
 * backup that cannot be read by the importer is not a backup.
 *
 * ### Photo entry names
 *
 * Entries are numbered, not named after their source URI: a `content://` URI is not a legal file
 * name, is often not unique, and can carry the photo's original folder structure into the archive.
 * The manifest maps every original URI to its entry, which is what lets a restore relink the
 * records to the extracted files.
 *
 * Streamed both ways — a 300-item vessel with photos is tens of megabytes and §17.3 will not have
 * it in one `ByteArray` on the main thread. (Encryption is the exception: authenticated encryption
 * needs the whole blob, which is why a passphrase-protected backup is bounded by memory and a plain
 * one is not.)
 */
object BackupArchive {

    const val ENTRY_MANIFEST: String = "manifest.json"
    const val ENTRY_PAYLOAD: String = "payload.json"
    const val PHOTO_DIR: String = "photos/"

    /** Bumped only when a change cannot be expressed as "a new manifest field with a default". */
    const val CURRENT_FORMAT_VERSION: Int = 1

    /** The `.dwbackup` extension of §18, and the MIME type SAF is asked to create. */
    const val EXTENSION: String = "dwbackup"
    const val MIME_TYPE: String = "application/octet-stream"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
    }

    /**
     * Write an archive.
     *
     * @param photoSources opens the bytes for one photo URI, or returns null when the file has
     *   gone. A missing photo is skipped and left out of the manifest rather than failing the
     *   backup: losing one JPEG must not cost the officer the register it belonged to (C10).
     * @return the manifest as written, i.e. with the photos that actually made it.
     */
    fun write(
        output: OutputStream,
        payloadJson: String,
        createdAtMillis: Long,
        appVersion: String,
        vesselIds: List<String>,
        photoUris: List<String>,
        photoSources: (String) -> InputStream?,
    ): BackupManifest {
        // Two passes: the photos are copied first into their entries, and the manifest — which has
        // to name them — is written last. A zip's entries have no required order, and the reader
        // below indexes by name, so "last" is free.
        val zip = ZipOutputStream(output)
        val photos = ArrayList<BackupPhoto>(photoUris.size)
        var index = 0
        for (uri in photoUris.distinct()) {
            val source = photoSources(uri) ?: continue
            index++
            val entryName = PHOTO_DIR + photoEntryName(index, uri)
            zip.putNextEntry(ZipEntry(entryName))
            val written = source.use { it.copyTo(zip) }
            zip.closeEntry()
            photos += BackupPhoto(uri = uri, entryName = entryName, sizeBytes = written)
        }

        zip.putNextEntry(ZipEntry(ENTRY_PAYLOAD))
        zip.write(payloadJson.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        val manifest = BackupManifest(
            createdAtMillis = createdAtMillis,
            appVersion = appVersion,
            vesselIds = vesselIds,
            photos = photos,
        )
        zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
        zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        return manifest
    }

    /**
     * Read an archive.
     *
     * @param photoSink receives each photo entry's bytes and returns the URI the restored file now
     *   lives at, or null to keep the original URI. Called with the entry name and the stream; the
     *   stream must not be closed by the sink (the zip owns it).
     * @return the contents, or null when the stream is not a readable archive or carries no
     *   payload — §17.4's "survives a corrupted import file".
     */
    fun read(
        input: InputStream,
        photoSink: (BackupPhoto, InputStream) -> String? = { _, _ -> null },
    ): BackupContents? = runCatching {
        val zip = ZipInputStream(input)
        var payloadJson: String? = null
        var manifest: BackupManifest? = null
        val pending = LinkedHashMap<String, ByteArray>()
        val relinked = HashMap<String, String>()

        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            when {
                entry.name == ENTRY_PAYLOAD -> payloadJson = zip.readBytes().toString(Charsets.UTF_8)
                entry.name == ENTRY_MANIFEST ->
                    manifest = runCatching {
                        json.decodeFromString<BackupManifest>(zip.readBytes().toString(Charsets.UTF_8))
                    }.getOrNull()
                // The manifest may not have been read yet, so photo bytes are held until the end
                // and handed to the sink once their manifest row is known.
                entry.name.startsWith(PHOTO_DIR) && !entry.isDirectory -> pending[entry.name] = zip.readBytes()
                else -> Unit
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        val payload = payloadJson ?: return@runCatching null
        val resolved = manifest ?: BackupManifest(createdAtMillis = 0L)
        for (photo in resolved.photos) {
            val bytes = pending[photo.entryName] ?: continue
            photoSink(photo, bytes.inputStream())?.let { relinked[photo.uri] = it }
        }
        BackupContents(manifest = resolved, payloadJson = payload, relinkedPhotoUris = relinked)
    }.getOrNull()

    /** Convenience for the small-file paths (encryption, tests): the archive as bytes. */
    fun writeToBytes(
        payloadJson: String,
        createdAtMillis: Long,
        appVersion: String,
        vesselIds: List<String>,
        photoUris: List<String>,
        photoSources: (String) -> InputStream?,
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        buffer.use {
            write(it, payloadJson, createdAtMillis, appVersion, vesselIds, photoUris, photoSources)
        }
        return buffer.toByteArray()
    }

    private fun photoEntryName(index: Int, uri: String): String {
        val suffix = uri.substringAfterLast('.', "").takeIf { it.length in 1..EXTENSION_MAX && it.all(Char::isLetterOrDigit) }
        return "%04d".format(index) + if (suffix != null) ".$suffix" else ""
    }

    private const val EXTENSION_MAX = 5
}

/** What one `.dwbackup` says about itself — written last, read first. */
@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupArchive.CURRENT_FORMAT_VERSION,
    val createdAtMillis: Long,
    val appVersion: String = "",
    val vesselIds: List<String> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
)

/** One archived photo: where it came from, where it went, how big it was. */
@Serializable
data class BackupPhoto(
    val uri: String,
    val entryName: String,
    val sizeBytes: Long = 0L,
)

/** A read archive: its manifest, its payload JSON, and any photo URIs the reader relinked. */
data class BackupContents(
    val manifest: BackupManifest,
    val payloadJson: String,
    /** Original URI → the URI the extracted copy now lives at. Empty when nothing was extracted. */
    val relinkedPhotoUris: Map<String, String> = emptyMap(),
)
