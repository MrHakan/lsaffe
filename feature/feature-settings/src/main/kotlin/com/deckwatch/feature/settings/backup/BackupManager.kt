package com.deckwatch.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.deckwatch.core.common.DispatcherProvider
import com.deckwatch.core.common.repository.VesselRepository
import com.deckwatch.core.datastore.UserPreferencesRepository
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.feature.report.AppVersionProvider
import com.deckwatch.feature.report.DeckWatchExportPayload
import com.deckwatch.feature.report.ExportScope
import com.deckwatch.feature.report.ImportApplier
import com.deckwatch.feature.report.ImportMerger
import com.deckwatch.feature.report.ImportOutcome
import com.deckwatch.feature.report.PayloadAssembler
import com.deckwatch.feature.report.PayloadParseResult
import com.deckwatch.feature.report.PayloadParser
import com.deckwatch.feature.report.PhotoTier
import com.deckwatch.feature.report.toJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup attempt did — every failure is a value, never an exception (C10, §17.4). */
sealed interface BackupOutcome {
    data class Written(val bytes: Long, val vessels: Int, val photos: Int, val encrypted: Boolean) : BackupOutcome
    data object NothingToBackUp : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
}

/** What a restore attempt did. */
sealed interface RestoreOutcome {
    data class Applied(val written: Int, val deletions: Int, val photos: Int) : RestoreOutcome

    /** The plan was refused before anything was written; nothing changed. */
    data class Rejected(val reason: String) : RestoreOutcome

    /** A write failed part-way; [unrecoverable] names what could not be withdrawn. */
    data class PartiallyApplied(val message: String, val unrecoverable: Int) : RestoreOutcome

    /** The file could not be opened, decrypted or parsed. */
    data class Unreadable(val reason: RestoreFailure) : RestoreOutcome
}

/** Why a restore never got as far as a plan. */
enum class RestoreFailure {
    /** SAF could not open the file at all. */
    UNREADABLE,

    /** The container is encrypted and no passphrase was given. */
    PASSPHRASE_REQUIRED,

    /** Wrong passphrase, or the authentication tag did not verify. */
    PASSPHRASE_WRONG,

    /** Not a `.dwbackup`, or a truncated one. */
    NOT_A_BACKUP,

    /** A readable archive whose payload this build's schema cannot accept — §13.5. */
    SCHEMA_MISMATCH,
}

/**
 * Manual and automatic backup — MASTER_PROMPT §18.
 *
 * ### What a backup contains
 *
 * Every vessel in the database, at [ExportScope.FULL_BACKUP], with **all** photos
 * ([PhotoTier.ALL]). `PayloadAssembler` works one vessel at a time (it is built for the §13.3
 * report scopes, which are always about one ship), so [assemblePayload] runs it per vessel and
 * merges the results; the global tables — user notes, user-defined types and user task definitions
 * — repeat in every vessel's payload and are de-duplicated by id on the way in.
 *
 * ### Restore
 *
 * Restore goes through **exactly the same pipeline as §13.5's file import**: parse with
 * `PayloadParser`, snapshot the database with `ImportApplier`, preview with `ImportMerger`, and
 * apply the preview's own default resolutions — which are "take theirs only when theirs is newer",
 * i.e. the required *skip records whose `updatedAt` is older*. There is no second merge
 * implementation in this module, and there must never be one: the day the two disagree is the day
 * an officer loses a week of rounds.
 *
 * The one thing restore adds is **photo relinking**. Photos come back out of the zip into
 * `filesDir/restored-photos/`, and every `photoUris` entry in the payload that names an archived
 * photo is rewritten to point at the extracted copy before the merge runs. Without that step a
 * restore onto a new phone would produce a complete register with every photo broken, because the
 * original `content://` URIs belong to the old device's gallery.
 *
 * ### The automatic weekly backup
 *
 * [autoBackup] writes into the SAF tree the officer chose (see `BackupFolder`) and prunes to the
 * last [AUTO_BACKUP_KEEP] files, matching §18's "keeping the last 8". It is deliberately *not*
 * scheduled by this class: scheduling belongs to the app's WorkManager wiring, and a backup that
 * fires without the officer knowing where it went is worse than no backup.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assembler: PayloadAssembler,
    private val applier: ImportApplier,
    private val vesselRepository: VesselRepository,
    private val preferences: UserPreferencesRepository,
    private val appVersionProvider: AppVersionProvider,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Write a full backup to a SAF destination the user picked with `ACTION_CREATE_DOCUMENT`.
     *
     * @param passphrase null for a plain zip; otherwise the archive is wrapped by [BackupCrypto].
     *   The array is zeroed by the caller — this class never keeps it.
     */
    suspend fun createBackup(destination: Uri, passphrase: CharArray? = null): BackupOutcome =
        withContext(dispatchers.io) {
            runCatching {
                val assembled = assemblePayload()
                if (assembled.payload.vessels.isEmpty()) return@runCatching BackupOutcome.NothingToBackUp
                val outcome = writeArchive(destination, assembled, passphrase)
                recordBackup(outcome)
                outcome
            }.getOrElse { error -> BackupOutcome.Failed(error.message ?: error::class.java.simpleName) }
        }

    /**
     * The §18 automatic weekly backup: write a dated file into [treeUri] and keep the last
     * [AUTO_BACKUP_KEEP].
     */
    suspend fun autoBackup(treeUri: Uri, passphrase: CharArray? = null): BackupOutcome =
        withContext(dispatchers.io) {
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                    ?: return@runCatching BackupOutcome.Failed("Backup folder is not available")
                if (!tree.canWrite()) return@runCatching BackupOutcome.Failed("Backup folder is not writable")
                val assembled = assemblePayload()
                if (assembled.payload.vessels.isEmpty()) return@runCatching BackupOutcome.NothingToBackUp
                val created = tree.createFile(BackupArchive.MIME_TYPE, autoBackupFileName())
                    ?: return@runCatching BackupOutcome.Failed("Could not create the backup file")
                val outcome = writeArchive(created.uri, assembled, passphrase)
                if (outcome is BackupOutcome.Written) prune(tree)
                recordBackup(outcome)
                outcome
            }.getOrElse { error -> BackupOutcome.Failed(error.message ?: error::class.java.simpleName) }
        }

    /** Read a `.dwbackup` and merge it into the database — §18's "restore with the same merge". */
    suspend fun restoreBackup(source: Uri, passphrase: CharArray? = null): RestoreOutcome =
        withContext(dispatchers.io) {
            val contents = when (val read = readArchive(source, passphrase)) {
                is ReadResult.Failed -> return@withContext RestoreOutcome.Unreadable(read.reason)
                is ReadResult.Ok -> read.contents
            }
            val parsed = PayloadParser.parseJson(contents.payloadJson)
            if (parsed !is PayloadParseResult.Parsed) {
                return@withContext RestoreOutcome.Unreadable(RestoreFailure.SCHEMA_MISMATCH)
            }
            val payload = relink(parsed.payload, contents.relinkedPhotoUris)
            val snapshot = applier.snapshot(payload)
            val preview = ImportMerger.preview(snapshot, payload)
            val plan = ImportMerger.plan(snapshot, payload, preview.defaultResolutions())
            when (val outcome = applier.apply(plan, snapshot)) {
                // A restore deliberately does NOT touch `lastBackupAt`: it is a record of the last
                // backup *taken*, and restoring somebody else's file is no evidence that this
                // install's data is safe anywhere. The §18 nudge stays on until a backup is made.
                is ImportOutcome.Applied -> RestoreOutcome.Applied(
                    written = outcome.written,
                    deletions = outcome.deletions,
                    photos = contents.relinkedPhotoUris.size,
                )

                is ImportOutcome.Rejected -> RestoreOutcome.Rejected(
                    outcome.violations.joinToString(separator = "; ") { it.toString() }
                        .ifBlank { "The backup references records that are not present" },
                )

                is ImportOutcome.RolledBack -> RestoreOutcome.PartiallyApplied(
                    message = outcome.message,
                    unrecoverable = outcome.unrecoverable.size,
                )
            }
        }

    /** A dry read: what a file says about itself, without writing anything. */
    suspend fun peek(source: Uri, passphrase: CharArray? = null): BackupManifest? =
        withContext(dispatchers.io) { (readArchive(source, passphrase) as? ReadResult.Ok)?.contents?.manifest }

    // ---------------------------------------------------------------- assemble

    private data class Assembled(val payload: DeckWatchExportPayload, val photoUris: List<String>)

    private suspend fun assemblePayload(): Assembled {
        val vessels = vesselRepository.observeVessels().first()
        var merged = DeckWatchExportPayload(
            appVersion = appVersionProvider.versionName(),
            scope = ExportScope.FULL_BACKUP.name,
        )
        val photos = LinkedHashSet<String>()
        for (vessel in vessels) {
            val part = assembler.build(vessel.id, ExportScope.FULL_BACKUP, PhotoTier.ALL)
            merged = merge(merged, part.payload)
            photos += part.photoUris
        }
        return Assembled(merged, photos.toList())
    }

    /**
     * Fold one vessel's payload into the accumulated one.
     *
     * Per-vessel lists concatenate; the global lists (task definitions, user notes, user-defined
     * types, bundled keys) are identical in every part and are de-duplicated by their identity.
     */
    private fun merge(into: DeckWatchExportPayload, part: DeckWatchExportPayload) = into.copy(
        generatedAtMillis = maxOf(into.generatedAtMillis, part.generatedAtMillis),
        vessels = into.vessels + part.vessels,
        decks = into.decks + part.decks,
        zones = into.zones + part.zones,
        categories = (into.categories + part.categories).distinctBy { it.id },
        equipmentCategoryLinks = (into.equipmentCategoryLinks + part.equipmentCategoryLinks).distinct(),
        equipment = (into.equipment + part.equipment).distinctBy { it.id },
        taskDefinitions = (into.taskDefinitions + part.taskDefinitions).distinctBy { it.key },
        bundledTaskDefinitionKeys = (into.bundledTaskDefinitionKeys + part.bundledTaskDefinitionKeys).distinct(),
        taskInstances = (into.taskInstances + part.taskInstances).distinctBy { it.id },
        rounds = into.rounds + part.rounds,
        roundItems = into.roundItems + part.roundItems,
        deficiencies = into.deficiencies + part.deficiencies,
        userNotes = (into.userNotes + part.userNotes).distinctBy { it.id },
        userDefinedTypes = (into.userDefinedTypes + part.userDefinedTypes).distinctBy { it.typeKey },
    )

    // ---------------------------------------------------------------- write

    private fun writeArchive(destination: Uri, assembled: Assembled, passphrase: CharArray?): BackupOutcome {
        val payloadJson = assembled.payload.toJson()
        val createdAt = System.currentTimeMillis()
        val vesselIds = assembled.payload.vessels.map { it.id }
        val appVersion = appVersionProvider.versionName()
        var photoCount = 0
        var bytes = 0L

        if (passphrase == null) {
            // Streamed: a plain backup is bounded by disk, not by heap (§17.3).
            val stream = context.contentResolver.openOutputStream(destination, "wt")
                ?: return BackupOutcome.Failed("Could not open the destination")
            stream.use { output ->
                val manifest = BackupArchive.write(
                    output = output,
                    payloadJson = payloadJson,
                    createdAtMillis = createdAt,
                    appVersion = appVersion,
                    vesselIds = vesselIds,
                    photoUris = assembled.photoUris,
                    photoSources = ::openPhoto,
                )
                photoCount = manifest.photos.size
            }
            bytes = sizeOf(destination)
        } else {
            // Authenticated encryption needs the whole archive at once; this path is bounded by
            // memory, which is why the plain path is not routed through it.
            val archive = BackupArchive.writeToBytes(
                payloadJson = payloadJson,
                createdAtMillis = createdAt,
                appVersion = appVersion,
                vesselIds = vesselIds,
                photoUris = assembled.photoUris,
                photoSources = ::openPhoto,
            )
            photoCount = assembled.photoUris.count { openPhoto(it) != null }
            val sealed = BackupCrypto.encrypt(archive, passphrase)
            val stream = context.contentResolver.openOutputStream(destination, "wt")
                ?: return BackupOutcome.Failed("Could not open the destination")
            stream.use { it.write(sealed) }
            bytes = sealed.size.toLong()
        }

        return BackupOutcome.Written(
            bytes = bytes,
            vessels = vesselIds.size,
            photos = photoCount,
            encrypted = passphrase != null,
        )
    }

    private fun openPhoto(uri: String): InputStream? = runCatching {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            null, "file" -> File(parsed.path ?: uri).takeIf { it.isFile }?.inputStream()
            else -> context.contentResolver.openInputStream(parsed)
        }
    }.getOrNull()

    private fun sizeOf(uri: Uri): Long =
        runCatching { DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L }.getOrDefault(0L)

    // ---------------------------------------------------------------- read

    private sealed interface ReadResult {
        data class Ok(val contents: BackupContents) : ReadResult
        data class Failed(val reason: RestoreFailure) : ReadResult
    }

    private fun readArchive(source: Uri, passphrase: CharArray?): ReadResult {
        val raw = runCatching {
            context.contentResolver.openInputStream(source)?.use(InputStream::readBytes)
        }.getOrNull() ?: return ReadResult.Failed(RestoreFailure.UNREADABLE)

        val archiveBytes = if (BackupCrypto.isEncrypted(raw)) {
            if (passphrase == null) return ReadResult.Failed(RestoreFailure.PASSPHRASE_REQUIRED)
            BackupCrypto.decrypt(raw, passphrase)
                ?: return ReadResult.Failed(RestoreFailure.PASSPHRASE_WRONG)
        } else {
            raw
        }

        val restoredDir = File(context.filesDir, RESTORED_PHOTO_DIR).apply { mkdirs() }
        val contents = BackupArchive.read(BufferedInputStream(archiveBytes.inputStream())) { photo, stream ->
            runCatching {
                val target = File(restoredDir, photo.entryName.substringAfterLast('/'))
                target.outputStream().use { stream.copyTo(it) }
                Uri.fromFile(target).toString()
            }.getOrNull()
        } ?: return ReadResult.Failed(RestoreFailure.NOT_A_BACKUP)

        return ReadResult.Ok(contents)
    }

    /**
     * Re-point every `photoUris` entry that names an archived photo at its restored copy.
     *
     * URIs the archive did not carry are left exactly as they are: on the same device they still
     * resolve, and rewriting them to nothing would turn a working photo into a broken one.
     */
    private fun relink(payload: DeckWatchExportPayload, mapping: Map<String, String>): DeckWatchExportPayload {
        if (mapping.isEmpty()) return payload
        fun remap(uris: List<String>) = uris.map { mapping[it] ?: it }
        return payload.copy(
            equipment = payload.equipment.map { item: Equipment -> item.copy(photoUris = remap(item.photoUris)) },
            taskInstances = payload.taskInstances.map { instance: TaskInstance ->
                instance.copy(
                    photoUris = remap(instance.photoUris),
                    attachmentUris = remap(instance.attachmentUris),
                )
            },
            roundItems = payload.roundItems.map { item: RoundItem -> item.copy(photoUris = remap(item.photoUris)) },
            deficiencies = payload.deficiencies.map { item: Deficiency -> item.copy(photoUris = remap(item.photoUris)) },
        )
    }

    // ---------------------------------------------------------------- housekeeping

    /** `lastBackupAt` is what silences the §18 day-30 nudge, so only a real write moves it. */
    private suspend fun recordBackup(outcome: BackupOutcome) {
        if (outcome is BackupOutcome.Written) preferences.setLastBackupAt(System.currentTimeMillis())
    }

    private fun prune(tree: DocumentFile) {
        val backups = tree.listFiles()
            .filter { it.isFile && it.name?.startsWith(AUTO_BACKUP_PREFIX) == true }
            .sortedByDescending { it.lastModified() }
        backups.drop(AUTO_BACKUP_KEEP).forEach { runCatching { it.delete() } }
    }

    private fun autoBackupFileName(): String {
        val stamp = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date())
        return "$AUTO_BACKUP_PREFIX$stamp.${BackupArchive.EXTENSION}"
    }

    companion object {
        /** §18: "keeping the last 8". */
        const val AUTO_BACKUP_KEEP: Int = 8

        /** Prefix of an automatic backup's file name; manual backups are named by the user. */
        const val AUTO_BACKUP_PREFIX: String = "DeckWatch_AutoBackup_"

        /** Where restored photos land, relative to `filesDir`. */
        const val RESTORED_PHOTO_DIR: String = "restored-photos"

        /** The §13.6 timestamp shape, reused so backups sort by name as well as by date. */
        const val FILE_STAMP_PATTERN: String = "yyyyMMdd_HHmm"

        /** The suggested file name for a manual backup — §13.6's naming convention. */
        fun manualBackupFileName(vesselName: String?, atMillis: Long = System.currentTimeMillis()): String {
            val stamp = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US).format(Date(atMillis))
            val ship = vesselName
                ?.uppercase(Locale.US)
                ?.replace(Regex("[^A-Z0-9]+"), "_")
                ?.trim('_')
                ?.takeIf { it.isNotBlank() }
                ?: "ALL"
            return "DeckWatch_${ship}_BACKUP_$stamp.${BackupArchive.EXTENSION}"
        }
    }
}
