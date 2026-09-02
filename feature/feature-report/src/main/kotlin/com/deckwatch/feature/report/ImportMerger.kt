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
import java.util.UUID

/**
 * The merge rules of §13.5, as pure functions over data. No repositories, no coroutines, no
 * Android — which is what lets the whole of the merge behaviour be covered by JVM unit tests
 * (§17.2 asks for ≥80% on the export/import serialiser).
 *
 * The rules, in the order they are applied:
 *
 * 1. **Merge by id.** A record present on only one side is kept or added; there is nothing to
 *    decide.
 * 2. **Identical content is not a conflict.** Re-importing the same file twice writes nothing.
 * 3. **A newer deletion wins, silently.** An incoming tombstone (`deletedAt` set) whose
 *    `updatedAt` is at least the local record's propagates the deletion instead of resurrecting
 *    the row. This is deliberately *not* a conflict: the other device already made the decision,
 *    and asking again is how a deleted item comes back to life on every sync.
 * 4. **Everything else is a conflict** and needs an explicit choice. Nothing is auto-overwritten.
 *    The suggestion is "take theirs" only when the incoming record is demonstrably newer;
 *    record types that carry no `updatedAt` at all suggest "keep mine", because with no timestamp
 *    the honest answer is that we cannot tell.
 */
object ImportMerger {

    /** Appended to the tag or name of a [ConflictResolution.KEEP_BOTH] duplicate — §13.5. */
    const val IMPORTED_SUFFIX: String = " (imported)"

    // ------------------------------------------------------------------ preview

    /** What an import would do, without doing any of it — §13.5. */
    fun preview(local: LocalSnapshot, incoming: DeckWatchExportPayload): ImportPreview {
        val diffs = diffAll(local, incoming)
        val deletions = deletionsToPropagate(local, incoming)
        return ImportPreview(
            payload = incoming,
            incoming = ImportCounts(
                diffs.associate { it.kind to (it.newRecords.size + it.unchanged.size + it.conflicts.size) },
            ),
            newRecords = ImportCounts(diffs.associate { it.kind to it.newRecords.size }),
            unchanged = ImportCounts(diffs.associate { it.kind to it.unchanged.size }),
            conflicts = diffs.flatMap { diff -> diff.conflicts.map { it.first } },
            propagatedDeletions = deletions.size,
        )
    }

    // ------------------------------------------------------------------ plan

    /**
     * Turn the preview plus the user's decisions into the complete set of writes.
     *
     * @param resolutions keyed by [conflictKey]; a conflict absent from the map falls back to its
     *   [ImportConflict.suggested] value, which is what the dialog shows by default.
     * @param newId supplies ids for [ConflictResolution.KEEP_BOTH] duplicates. Injected so a test
     *   can assert on them.
     */
    fun plan(
        local: LocalSnapshot,
        incoming: DeckWatchExportPayload,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        newId: () -> String = { UUID.randomUUID().toString() },
    ): WritePlan {
        val diffs = diffAll(local, incoming).associateBy { it.kind }

        // Pass 1 — allocate the fresh ids, so every reference can be re-pointed in pass 2.
        val remap = mutableMapOf<String, String>()
        for (diff in diffs.values) {
            for ((conflict, _) in diff.conflicts) {
                if (resolutionFor(conflict, resolutions) == ConflictResolution.KEEP_BOTH) {
                    remap[conflict.id] = newId()
                }
            }
        }
        fun ref(id: String?): String? = id?.let { remap[it] ?: it }

        // Pass 2 — the records that survive the user's choices, with references re-pointed.
        fun <T> selected(kind: RecordKind, rename: (T, String) -> T): List<T> {
            @Suppress("UNCHECKED_CAST")
            val diff = diffs[kind] as? KindDiff<T> ?: return emptyList()
            return diff.newRecords + diff.conflicts.mapNotNull { (conflict, record) ->
                when (resolutionFor(conflict, resolutions)) {
                    ConflictResolution.KEEP_MINE -> null
                    ConflictResolution.TAKE_THEIRS -> record
                    ConflictResolution.KEEP_BOTH -> remap[conflict.id]?.let { rename(record, it) }
                }
            }
        }

        val vessels = selected<Vessel>(RecordKind.VESSEL) { v, id ->
            v.copy(id = id, name = v.name + IMPORTED_SUFFIX, isActive = false)
        }
        val decks = selected<Deck>(RecordKind.DECK) { d, id ->
            d.copy(id = id, name = d.name + IMPORTED_SUFFIX)
        }.map { it.copy(vesselId = ref(it.vesselId).orEmpty()) }
        val zones = selected<Zone>(RecordKind.ZONE) { z, id ->
            z.copy(id = id, name = z.name + IMPORTED_SUFFIX)
        }.map { it.copy(deckId = ref(it.deckId).orEmpty()) }
        val categories = selected<Category>(RecordKind.CATEGORY) { c, id ->
            c.copy(id = id, name = c.name + IMPORTED_SUFFIX)
        }.map { it.copy(vesselId = ref(it.vesselId)) }
        val userTypes = selected<EquipmentType>(RecordKind.USER_TYPE) { t, id ->
            t.copy(typeKey = id, nameEn = t.nameEn + IMPORTED_SUFFIX)
        }
        val definitions = selected<TaskDefinition>(RecordKind.TASK_DEFINITION) { d, id ->
            d.copy(key = id, titleEn = d.titleEn + IMPORTED_SUFFIX)
        }
        val equipment = selected<Equipment>(RecordKind.EQUIPMENT) { e, id ->
            e.copy(
                id = id,
                tag = e.tag + IMPORTED_SUFFIX,
                name = e.name?.plus(IMPORTED_SUFFIX),
            )
        }.map {
            it.copy(
                vesselId = ref(it.vesselId).orEmpty(),
                deckId = ref(it.deckId),
                zoneId = ref(it.zoneId),
                parentId = ref(it.parentId),
            )
        }
        val links = selected<EquipmentCategoryLink>(RecordKind.CATEGORY_LINK) { link, _ -> link }
            .map {
                EquipmentCategoryLink(
                    equipmentId = ref(it.equipmentId).orEmpty(),
                    categoryId = ref(it.categoryId).orEmpty(),
                )
            }
        val instances = selected<TaskInstance>(RecordKind.TASK_INSTANCE) { i, id -> i.copy(id = id) }
            .map { it.copy(equipmentId = ref(it.equipmentId).orEmpty()) }
        val rounds = selected<Round>(RecordKind.ROUND) { r, id ->
            r.copy(id = id, title = r.title + IMPORTED_SUFFIX)
        }.map { it.copy(vesselId = ref(it.vesselId).orEmpty()) }
        val roundItems = selected<RoundItem>(RecordKind.ROUND_ITEM) { i, id -> i.copy(id = id) }
            .map { it.copy(roundId = ref(it.roundId).orEmpty(), equipmentId = ref(it.equipmentId).orEmpty()) }
        val deficiencies = selected<Deficiency>(RecordKind.DEFICIENCY) { d, id ->
            d.copy(id = id, title = d.title + IMPORTED_SUFFIX)
        }.map { it.copy(vesselId = ref(it.vesselId).orEmpty(), equipmentId = ref(it.equipmentId)) }
        val notes = selected<UserNote>(RecordKind.USER_NOTE) { n, id ->
            n.copy(id = id, title = n.title + IMPORTED_SUFFIX)
        }

        return WritePlan(
            vessels = vessels,
            decks = decks,
            zones = zones,
            categories = categories,
            userTypes = userTypes,
            taskDefinitions = definitions,
            equipment = equipment,
            categoryLinks = links,
            taskInstances = instances,
            rounds = rounds,
            roundItems = roundItems,
            deficiencies = deficiencies,
            userNotes = notes,
            equipmentDeletions = deletionsToPropagate(local, incoming),
            idRemap = remap,
        )
    }

    // ------------------------------------------------------------------ validation

    /**
     * Check that every foreign key in [plan] resolves — either to another record in the plan or to
     * one already in the database. A single violation refuses the whole import (§13.5's
     * "either the whole import applies or none of it does"), which is far better than writing
     * eleven of twelve tables and leaving an orphaned deck behind.
     */
    fun validate(plan: WritePlan, local: LocalSnapshot): List<FkViolation> {
        val vesselIds = local.vessels.keys + plan.vessels.map { it.id }
        val deckIds = local.decks.keys + plan.decks.map { it.id }
        val zoneIds = local.zones.keys + plan.zones.map { it.id }
        val categoryIds = local.categories.keys + plan.categories.map { it.id }
        val equipmentIds = local.equipment.keys + plan.equipment.map { it.id }
        val roundIds = local.rounds.keys + plan.rounds.map { it.id }

        return buildList {
            plan.decks.forEach {
                checkRef(this, it.vesselId in vesselIds, RecordKind.DECK, it.id, "vesselId", it.vesselId)
            }
            plan.zones.forEach {
                checkRef(this, it.deckId in deckIds, RecordKind.ZONE, it.id, "deckId", it.deckId)
            }
            plan.categories.forEach { category ->
                val vesselId = category.vesselId
                if (vesselId != null) {
                    checkRef(this, vesselId in vesselIds, RecordKind.CATEGORY, category.id, "vesselId", vesselId)
                }
            }
            plan.equipment.forEach { item ->
                checkRef(this, item.vesselId in vesselIds, RecordKind.EQUIPMENT, item.id, "vesselId", item.vesselId)
                item.deckId?.let {
                    checkRef(this, it in deckIds, RecordKind.EQUIPMENT, item.id, "deckId", it)
                }
                item.zoneId?.let {
                    checkRef(this, it in zoneIds, RecordKind.EQUIPMENT, item.id, "zoneId", it)
                }
                item.parentId?.let {
                    checkRef(this, it in equipmentIds, RecordKind.EQUIPMENT, item.id, "parentId", it)
                }
            }
            plan.categoryLinks.forEach { link ->
                checkRef(
                    this, link.equipmentId in equipmentIds,
                    RecordKind.CATEGORY_LINK, link.equipmentId, "equipmentId", link.equipmentId,
                )
                checkRef(
                    this, link.categoryId in categoryIds,
                    RecordKind.CATEGORY_LINK, link.equipmentId, "categoryId", link.categoryId,
                )
            }
            plan.taskInstances.forEach {
                checkRef(
                    this, it.equipmentId in equipmentIds,
                    RecordKind.TASK_INSTANCE, it.id, "equipmentId", it.equipmentId,
                )
            }
            plan.rounds.forEach {
                checkRef(this, it.vesselId in vesselIds, RecordKind.ROUND, it.id, "vesselId", it.vesselId)
            }
            plan.roundItems.forEach {
                checkRef(this, it.roundId in roundIds, RecordKind.ROUND_ITEM, it.id, "roundId", it.roundId)
                checkRef(
                    this, it.equipmentId in equipmentIds,
                    RecordKind.ROUND_ITEM, it.id, "equipmentId", it.equipmentId,
                )
            }
            plan.deficiencies.forEach { deficiency ->
                checkRef(
                    this, deficiency.vesselId in vesselIds,
                    RecordKind.DEFICIENCY, deficiency.id, "vesselId", deficiency.vesselId,
                )
                deficiency.equipmentId?.let {
                    checkRef(this, it in equipmentIds, RecordKind.DEFICIENCY, deficiency.id, "equipmentId", it)
                }
            }
            plan.equipmentDeletions.forEach {
                checkRef(
                    this, it.equipmentId in local.equipment.keys,
                    RecordKind.EQUIPMENT, it.equipmentId, "deletion target", it.equipmentId,
                )
            }
        }
    }

    private fun checkRef(
        sink: MutableList<FkViolation>,
        condition: Boolean,
        kind: RecordKind,
        recordId: String,
        field: String,
        missingId: String,
    ) {
        if (!condition) sink += FkViolation(kind, recordId, field, missingId)
    }

    // ------------------------------------------------------------------ diffing

    private class KindDiff<T>(
        val kind: RecordKind,
        val newRecords: List<T>,
        val unchanged: List<T>,
        val conflicts: List<Pair<ImportConflict, T>>,
    )

    private fun diffAll(local: LocalSnapshot, incoming: DeckWatchExportPayload): List<KindDiff<*>> =
        listOf(
            diff(RecordKind.VESSEL, incoming.vessels, local.vessels, { it.id }, { it.name }, { it.updatedAt }),
            diff(RecordKind.DECK, incoming.decks, local.decks, { it.id }, { it.name }, { it.updatedAt }),
            diff(RecordKind.ZONE, incoming.zones, local.zones, { it.id }, { it.name }, { null }),
            diff(RecordKind.CATEGORY, incoming.categories, local.categories, { it.id }, { it.name }, { null }),
            diff(
                RecordKind.USER_TYPE, incoming.userDefinedTypes, local.userTypes,
                { it.typeKey }, { it.nameEn }, { null },
            ),
            diff(
                RecordKind.TASK_DEFINITION, incoming.taskDefinitions, local.taskDefinitions,
                { it.key }, { it.titleEn }, { null },
            ),
            equipmentDiff(local, incoming),
            linkDiff(local, incoming),
            diff(
                RecordKind.TASK_INSTANCE, incoming.taskInstances, local.taskInstances,
                { it.id }, { it.taskKey }, { it.updatedAt },
            ),
            diff(RecordKind.ROUND, incoming.rounds, local.rounds, { it.id }, { it.title }, { null }),
            diff(
                RecordKind.ROUND_ITEM, incoming.roundItems, local.roundItems,
                { it.id }, { it.id }, { null },
            ),
            diff(
                RecordKind.DEFICIENCY, incoming.deficiencies, local.deficiencies,
                { it.id }, { it.title }, { null },
            ),
            diff(
                RecordKind.USER_NOTE, incoming.userNotes, local.userNotes,
                { it.id }, { it.title }, { it.updatedAt },
            ),
        )

    private fun <T> diff(
        kind: RecordKind,
        incoming: List<T>,
        local: Map<String, T>,
        idOf: (T) -> String,
        labelOf: (T) -> String,
        updatedAtOf: (T) -> Long?,
    ): KindDiff<T> {
        val newRecords = mutableListOf<T>()
        val unchanged = mutableListOf<T>()
        val conflicts = mutableListOf<Pair<ImportConflict, T>>()
        for (record in incoming) {
            val id = idOf(record)
            val mine = local[id]
            when {
                mine == null -> newRecords += record
                mine == record -> unchanged += record
                else -> {
                    val mineAt = updatedAtOf(mine)
                    val theirsAt = updatedAtOf(record)
                    conflicts += ImportConflict(
                        kind = kind,
                        id = id,
                        label = labelOf(record),
                        mineUpdatedAt = mineAt,
                        theirsUpdatedAt = theirsAt,
                        suggested = suggest(mineAt, theirsAt),
                    ) to record
                }
            }
        }
        return KindDiff(kind, newRecords, unchanged, conflicts)
    }

    /**
     * Equipment carries the soft-delete rule, so it gets its own diff.
     *
     * An incoming tombstone at least as new as the local record is *not* reported as a conflict —
     * it is counted as a propagated deletion and applied by [deletionsToPropagate]. An incoming
     * tombstone that is *older* than a locally edited record still is a conflict: the officer
     * edited it after the other device deleted it, and that is a real disagreement.
     */
    private fun equipmentDiff(local: LocalSnapshot, incoming: DeckWatchExportPayload): KindDiff<Equipment> {
        val newRecords = mutableListOf<Equipment>()
        val unchanged = mutableListOf<Equipment>()
        val conflicts = mutableListOf<Pair<ImportConflict, Equipment>>()
        for (record in incoming.equipment) {
            val mine = local.equipment[record.id]
            when {
                mine == null -> newRecords += record
                mine == record -> unchanged += record
                record.deletedAt != null && record.updatedAt >= mine.updatedAt -> Unit // propagated
                else -> conflicts += ImportConflict(
                    kind = RecordKind.EQUIPMENT,
                    id = record.id,
                    label = listOfNotNull(record.tag, record.name).joinToString(" — "),
                    mineUpdatedAt = mine.updatedAt,
                    theirsUpdatedAt = record.updatedAt,
                    incomingIsDeletion = record.deletedAt != null,
                    suggested = suggest(mine.updatedAt, record.updatedAt),
                ) to record
            }
        }
        return KindDiff(RecordKind.EQUIPMENT, newRecords, unchanged, conflicts)
    }

    /** A category link is its own key; it either exists or it does not, so it cannot conflict. */
    private fun linkDiff(
        local: LocalSnapshot,
        incoming: DeckWatchExportPayload,
    ): KindDiff<EquipmentCategoryLink> {
        val (unchanged, newRecords) = incoming.equipmentCategoryLinks
            .distinct()
            .partition { it in local.categoryLinks }
        return KindDiff(RecordKind.CATEGORY_LINK, newRecords, unchanged, emptyList())
    }

    /**
     * Deletions to apply locally: incoming tombstones for rows this device still holds as live, or
     * as deleted at an earlier moment — §13.5 ("handle soft-deleted records correctly so a
     * deletion on one device propagates rather than resurrecting").
     */
    private fun deletionsToPropagate(
        local: LocalSnapshot,
        incoming: DeckWatchExportPayload,
    ): List<EquipmentDeletion> = incoming.equipment.mapNotNull { record ->
        val deletedAt = record.deletedAt ?: return@mapNotNull null
        val mine = local.equipment[record.id] ?: return@mapNotNull null
        val mineDeletedAt = mine.deletedAt
        val newer = mineDeletedAt == null || mineDeletedAt < deletedAt
        if (newer && record.updatedAt >= mine.updatedAt) {
            EquipmentDeletion(record.id, deletedAt)
        } else {
            null
        }
    }

    private fun suggest(mineUpdatedAt: Long?, theirsUpdatedAt: Long?): ConflictResolution =
        if (mineUpdatedAt != null && theirsUpdatedAt != null && theirsUpdatedAt > mineUpdatedAt) {
            ConflictResolution.TAKE_THEIRS
        } else {
            ConflictResolution.KEEP_MINE
        }

    private fun resolutionFor(
        conflict: ImportConflict,
        resolutions: Map<String, ConflictResolution>,
    ): ConflictResolution = resolutions[conflictKey(conflict.kind, conflict.id)] ?: conflict.suggested
}
