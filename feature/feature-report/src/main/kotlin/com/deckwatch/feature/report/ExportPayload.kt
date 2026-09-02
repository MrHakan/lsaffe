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
import com.deckwatch.feature.inspection.DueExportRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The complete dataset carried by an exported `.html` file — the `deckwatch-data` block of §13.2,
 * and the same bytes the JSON export writes without the HTML wrapper.
 *
 * The payload **reuses the `core-model` records directly**: no parallel DTO layer to drift out of
 * step with the database. That is what makes an export re-importable by id with a per-record
 * `updatedAt` comparison (§13.5).
 *
 * Every field has a default. Combined with [PayloadJson]'s `ignoreUnknownKeys` and
 * `coerceInputValues` that means a file written by an older *or* a newer build of DeckWatch still
 * parses into whatever this build understands, instead of throwing — §17.4.
 *
 * ### What is deliberately *not* in here
 * Photo bytes. The static HTML embeds each photo once as a `data:` URI (§13.2); duplicating the
 * same base64 inside this JSON would roughly double the file and break the "under 10 MB" bar of
 * §17.3. [Equipment.photoUris] and friends therefore travel as the *references* recorded on the
 * source device: an import restores records, not images. The photo-carrying backup is the
 * `.dwbackup` zip of §18.
 */
@Serializable
data class DeckWatchExportPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "",
    val generatedAtMillis: Long = 0L,
    /** Name of the [ExportScope] this payload was produced for; informational only. */
    val scope: String = ExportScope.FULL_BACKUP.name,
    val vessels: List<Vessel> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** The `equipment_category_xref` rows of §6.4. */
    val equipmentCategoryLinks: List<EquipmentCategoryLink> = emptyList(),
    /**
     * Every equipment row **including soft-deleted ones** (`deletedAt` set), so a deletion made on
     * one device propagates on import rather than being resurrected by the other side — §13.5.
     */
    val equipment: List<Equipment> = emptyList(),
    /**
     * User-defined task definitions only. Bundled ones are seed content on every install, so
     * shipping them would bloat the file and risk overwriting a corrected interval rule with a
     * stale copy; their keys travel in [bundledTaskDefinitionKeys] instead, purely so an importer
     * can report "this vessel also relies on 34 bundled tasks".
     */
    val taskDefinitions: List<TaskDefinition> = emptyList(),
    val bundledTaskDefinitionKeys: List<String> = emptyList(),
    val taskInstances: List<TaskInstance> = emptyList(),
    val rounds: List<Round> = emptyList(),
    val roundItems: List<RoundItem> = emptyList(),
    val deficiencies: List<Deficiency> = emptyList(),
    val userNotes: List<UserNote> = emptyList(),
    /** User-defined catalogue entries — §9.2's escape hatch; bundled types are seed content. */
    val userDefinedTypes: List<EquipmentType> = emptyList(),
    /** Present only on a [ExportScope.DUE_LIST] export — the Due tab's snapshot (§12). */
    val dueList: DueExportRequest? = null,
) {
    companion object {
        /**
         * Bumped only when a change cannot be expressed as "a new field with a default".
         * An importer refuses any other value gracefully — §13.5.
         */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** One `equipment_category_xref` row — §6.4. A pair with names, so the JSON reads. */
@Serializable
data class EquipmentCategoryLink(val equipmentId: String, val categoryId: String)

/**
 * The one JSON configuration used for both writing and reading a payload.
 *
 * * `encodeDefaults` — an exported file states every field, so it is readable on its own.
 * * `ignoreUnknownKeys` — a file from a newer build parses; its extra fields are dropped.
 * * `coerceInputValues` — a null or out-of-range enum lands on the property default instead of
 *   throwing, which is what keeps a hand-edited or partially corrupted file importable (§17.4).
 * * `explicitNulls = false` — nullable fields at null are omitted, which trims the file noticeably.
 */
val PayloadJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    prettyPrint = false
}

/** Convenience: the payload as compact JSON. */
fun DeckWatchExportPayload.toJson(): String = PayloadJson.encodeToString(this)
