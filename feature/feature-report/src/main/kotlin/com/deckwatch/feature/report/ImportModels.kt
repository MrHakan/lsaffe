package com.deckwatch.feature.report

import com.deckwatch.core.model.Category
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Deficiency
import com.deckwatch.core.model.Equipment
import com.deckwatch.core.model.EquipmentType
import com.deckwatch.core.model.Round
import com.deckwatch.core.model.RoundItem
import com.deckwatch.core.model.TaskDefinition
import com.deckwatch.core.model.TaskInstance
import com.deckwatch.core.model.UserNote
import com.deckwatch.core.model.Vessel
import com.deckwatch.core.model.Zone

/** The record types an import can carry, in dependency order — §13.5's write order. */
enum class RecordKind {
    VESSEL,
    DECK,
    ZONE,
    CATEGORY,
    USER_TYPE,
    TASK_DEFINITION,
    EQUIPMENT,
    CATEGORY_LINK,
    TASK_INSTANCE,
    ROUND,
    ROUND_ITEM,
    DEFICIENCY,
    USER_NOTE,
}

/** How the user chose to settle one conflict — §13.5. */
enum class ConflictResolution {
    /** Discard the incoming record; the local one stands. */
    KEEP_MINE,

    /** Overwrite the local record with the incoming one. */
    TAKE_THEIRS,

    /**
     * Keep both: the incoming record is written under a fresh id with `" (imported)"` appended to
     * its tag or name, and everything in the file that referenced it is re-pointed at the copy.
     */
    KEEP_BOTH,
}

/**
 * One record that exists on both sides with different content — §13.5.
 *
 * @param label what the officer sees in the list: a tag, a vessel name, a deck name.
 * @param mineUpdatedAt / theirsUpdatedAt epoch-millis, or null for record types that carry no
 *   timestamp (zones, categories, rounds, round items, deficiencies). For those, "which is newer"
 *   is genuinely unknowable and the suggestion is [ConflictResolution.KEEP_MINE].
 * @param incomingIsDeletion true when the incoming side is a tombstone the local side has not
 *   applied — accepting it deletes the local record rather than editing it.
 */
data class ImportConflict(
    val kind: RecordKind,
    val id: String,
    val label: String,
    val mineUpdatedAt: Long? = null,
    val theirsUpdatedAt: Long? = null,
    val incomingIsDeletion: Boolean = false,
    val suggested: ConflictResolution = ConflictResolution.KEEP_MINE,
)

/** Per-kind record counts, for the preview dialog's "N vessels, N decks, N equipment" line. */
data class ImportCounts(val byKind: Map<RecordKind, Int> = emptyMap()) {
    operator fun get(kind: RecordKind): Int = byKind[kind] ?: 0
    val total: Int get() = byKind.values.sum()
}

/**
 * What an import *would* do, shown before anything is written — §13.5 ("show a preview and merge
 * dialog before writing anything").
 *
 * @param incoming everything the file contains, by kind.
 * @param newRecords the subset that does not exist locally and will simply be added.
 * @param unchanged records identical on both sides; nothing is written for them.
 * @param conflicts records that differ and need an explicit choice.
 * @param propagatedDeletions incoming tombstones newer than the local record, which are applied
 *   without asking because a deletion the other device already made is not a conflict — §13.5.
 */
data class ImportPreview(
    val payload: DeckWatchExportPayload,
    val incoming: ImportCounts = ImportCounts(),
    val newRecords: ImportCounts = ImportCounts(),
    val unchanged: ImportCounts = ImportCounts(),
    val conflicts: List<ImportConflict> = emptyList(),
    val propagatedDeletions: Int = 0,
) {
    /** Default resolutions: what the dialog starts with before the user touches anything. */
    fun defaultResolutions(): Map<String, ConflictResolution> =
        conflicts.associate { conflictKey(it.kind, it.id) to it.suggested }
}

/** Conflicts are keyed by kind + id: two kinds may legitimately share an id space. */
fun conflictKey(kind: RecordKind, id: String): String = "${kind.name}:$id"

/**
 * Everything currently in the database that an import could collide with.
 *
 * Loaded once, up front, and used for three things: conflict detection, foreign-key validation of
 * the write plan, and the rollback snapshot. Holding it in memory is what lets the applier restore
 * an overwritten record without a database transaction it does not have — see [ImportApplier].
 */
data class LocalSnapshot(
    val vessels: Map<String, Vessel> = emptyMap(),
    val decks: Map<String, Deck> = emptyMap(),
    val zones: Map<String, Zone> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val userTypes: Map<String, EquipmentType> = emptyMap(),
    val taskDefinitions: Map<String, TaskDefinition> = emptyMap(),
    val equipment: Map<String, Equipment> = emptyMap(),
    val categoryLinks: Set<EquipmentCategoryLink> = emptySet(),
    val taskInstances: Map<String, TaskInstance> = emptyMap(),
    val rounds: Map<String, Round> = emptyMap(),
    val roundItems: Map<String, RoundItem> = emptyMap(),
    val deficiencies: Map<String, Deficiency> = emptyMap(),
    val userNotes: Map<String, UserNote> = emptyMap(),
)

/**
 * Everything the import will write, already resolved and in dependency order — §13.5.
 *
 * Built completely before a single write happens. That is the whole point: the plan can be
 * validated ([ImportMerger.validate]) while the database is still untouched, so the overwhelming
 * majority of bad imports are rejected before they can do anything at all.
 */
data class WritePlan(
    val vessels: List<Vessel> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val categories: List<Category> = emptyList(),
    val userTypes: List<EquipmentType> = emptyList(),
    val taskDefinitions: List<TaskDefinition> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val categoryLinks: List<EquipmentCategoryLink> = emptyList(),
    val taskInstances: List<TaskInstance> = emptyList(),
    val rounds: List<Round> = emptyList(),
    val roundItems: List<RoundItem> = emptyList(),
    val deficiencies: List<Deficiency> = emptyList(),
    val userNotes: List<UserNote> = emptyList(),
    /** Local equipment ids to soft-delete, with the incoming `deletedAt` that justified it. */
    val equipmentDeletions: List<EquipmentDeletion> = emptyList(),
    /** Incoming id -> the fresh id it was written under, for [ConflictResolution.KEEP_BOTH]. */
    val idRemap: Map<String, String> = emptyMap(),
) {
    val writeCount: Int
        get() = vessels.size + decks.size + zones.size + categories.size + userTypes.size +
            taskDefinitions.size + equipment.size + categoryLinks.size + taskInstances.size +
            rounds.size + roundItems.size + deficiencies.size + userNotes.size +
            equipmentDeletions.size
}

/** A deletion to propagate: soft-delete [equipmentId] locally, stamped with [deletedAt]. */
data class EquipmentDeletion(val equipmentId: String, val deletedAt: Long)

/**
 * A reference in the write plan that resolves to nothing — neither in the plan nor in the local
 * database. The import is refused whole rather than written half.
 */
data class FkViolation(
    val kind: RecordKind,
    val recordId: String,
    val field: String,
    val missingId: String,
) {
    /** English, loggable; the UI shows it under a localised heading. */
    override fun toString(): String =
        "${kind.name} $recordId references a missing $field ($missingId)"
}

/** The outcome of applying a plan — §13.5's transactional promise, with its real limits. */
sealed interface ImportOutcome {
    /** Everything in the plan was written. */
    data class Applied(val written: Int, val deletions: Int) : ImportOutcome

    /** The plan was refused before anything was written. Nothing changed. */
    data class Rejected(val violations: List<FkViolation>) : ImportOutcome

    /**
     * A write failed part-way and the applier rolled back what it could.
     *
     * @param restored records put back or removed by the rollback.
     * @param unrecoverable rows this module cannot remove through the repository interfaces —
     *   see [ImportApplier]'s documented guarantee.
     */
    data class RolledBack(
        val message: String,
        val restored: Int,
        val unrecoverable: List<String>,
    ) : ImportOutcome
}
