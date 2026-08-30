package com.deckwatch.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.deckwatch.core.model.AttributeDefinition
import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.RegulationSection
import com.deckwatch.core.model.TechnicalNote
import com.deckwatch.core.model.VerificationStatus

/**
 * One entry of the equipment type catalogue — MASTER_PROMPT §9.1.
 *
 * Bundled content is seeded from `equipment_catalogue.json` on first run and re-seeded on a
 * content-version bump; rows with [isUserDefined] `true` are the user's own types (§9.2 "Other /
 * user-defined") and must survive re-seeding.
 *
 * [attributeSchema], [taskKeys], [regulationRefs] and [commonPscFindings] are stored as JSON text
 * columns via [com.deckwatch.core.database.converter.DeckWatchTypeConverters]; the schema is
 * open-ended data (§9.3) and would otherwise need a table per field kind.
 *
 * The `group` property maps to the column `typeGroup` because `group` is a reserved SQL word.
 */
@Entity(tableName = "equipment_types", indices = [Index("typeGroup"), Index("symbolKey")])
data class EquipmentTypeEntity(
    @PrimaryKey val typeKey: String,
    @ColumnInfo(name = "typeGroup") val group: EquipmentGroup,
    val subGroup: String,
    val nameEn: String,
    val nameTr: String,
    val symbolKey: String,
    /** e.g. "FE" — seeds the auto-suggested tag `FE-UD-03` (§7.5 step 3). */
    val defaultTagPrefix: String,
    val attributeSchema: List<AttributeDefinition>,
    val taskKeys: List<String>,
    val regulationRefs: List<String>,
    val helpTextEn: String,
    val helpTextTr: String,
    val commonPscFindings: List<String>,
    /**
     * The equipment guide of §9.1. Defaulted so that the version-2 migration can add the column
     * to an installed database without a value for every row; the next content import fills it.
     */
    val technicalNotes: List<TechnicalNote> = emptyList(),
    val isUserDefined: Boolean,
)

/**
 * A bundled regulatory note card — MASTER_PROMPT §8.2.
 *
 * [what] / [howOften] / [byWhom] / [evidence] are the mandatory quadrant of every card and are
 * non-null by contract. [verificationStatus] and [lastReviewed] carry §8.5: never assert a figure
 * the content could not confirm against the instrument.
 */
@Entity(tableName = "regulation_cards", indices = [Index("section"), Index("citation")])
data class RegulationCardEntity(
    @PrimaryKey val refKey: String,
    val section: RegulationSection,
    /** e.g. "SOLAS III/20.6" — the card badge. */
    val citation: String,
    val title: String,
    val what: String,
    val howOften: String,
    val byWhom: String,
    val evidence: String,
    val detailBullets: List<String>,
    /** Flag code (RMI/LIB/PAN) -> difference note. Empty when there is no real difference. */
    val flagNotes: Map<String, String>,
    val appliesToTypeKeys: List<String>,
    val sourceRef: String,
    val contentVersion: Int,
    val lastReviewed: String,
    val verificationStatus: VerificationStatus,
    /** Optional Turkish plain-language summary — C8 keeps quoted terminology in English. */
    val summaryTr: String,
    /** For FLAG cards: the notice revision and capture date — §8.5. */
    val revisionNote: String,
)

/** A bundled inspection round template — MASTER_PROMPT §19 item 5. */
@Entity(tableName = "round_templates")
data class RoundTemplateEntity(
    @PrimaryKey val key: String,
    val titleEn: String,
    val titleTr: String,
    val includesTypeKeys: List<String>,
    val includesGroups: List<EquipmentGroup>,
    val descriptionEn: String,
)

/**
 * The user's own note — MASTER_PROMPT §8.1 "MY NOTES".
 *
 * A note may hang off a regulation card, off an equipment type, off both, or off neither
 * (a free-standing note in a folder).
 */
@Entity(
    tableName = "user_notes",
    indices = [Index("regulationRefKey"), Index("equipmentTypeKey"), Index("folder")],
)
data class UserNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val folder: String,
    val regulationRefKey: String?,
    val equipmentTypeKey: String?,
    val isFavourite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
