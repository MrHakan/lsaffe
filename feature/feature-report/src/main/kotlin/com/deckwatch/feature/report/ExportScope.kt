package com.deckwatch.feature.report

import kotlinx.serialization.Serializable

/** The six export scopes of §13.3. */
@Serializable
enum class ExportScope {
    /** Everything. This is the re-importable one. */
    FULL_BACKUP,

    /** The current Due-tab filter as a printable table — fed by `DueExportRequest`. */
    DUE_LIST,

    /** One round: items, conditions, remarks, photos, signature block. */
    ROUND_REPORT,

    /** Open deficiencies with photos and target dates. */
    DEFICIENCY_REPORT,

    /** One deck: the plan as SVG with numbered markers plus a legend table. */
    DECK_SHEET,

    /** Equipment register + certificate status + last 12 months of rounds + open deficiencies. */
    PSC_SURVEY_PACK,
    ;

    /** The `{REPORTTYPE}` slug of the §13.6 filename. */
    val fileSlug: String
        get() = when (this) {
            FULL_BACKUP -> "FULL"
            DUE_LIST -> "DUE"
            ROUND_REPORT -> "ROUND"
            DEFICIENCY_REPORT -> "DEFICIENCIES"
            DECK_SHEET -> "DECK"
            PSC_SURVEY_PACK -> "PSC"
        }

    /** True when the scope needs a deck picked before it can be generated. */
    val needsDeck: Boolean get() = this == DECK_SHEET

    /** True when the scope needs a round picked before it can be generated. */
    val needsRound: Boolean get() = this == ROUND_REPORT
}

/** Photo embedding tier offered by the export dialog — §13.2. */
@Serializable
enum class PhotoTier {
    /** No `data:` URIs at all; the smallest file by a wide margin. */
    NONE,

    /** Only photos attached to deficiencies and to round items that recorded one. */
    DEFICIENCY_ONLY,

    /** Every photo on every record. */
    ALL,
}

/**
 * Everything the user chose in the export dialog. Kept separate from the payload so the same
 * assembled data can be rendered at several photo tiers without re-reading the database.
 */
data class ExportOptions(
    val scope: ExportScope = ExportScope.FULL_BACKUP,
    val photoTier: PhotoTier = PhotoTier.NONE,
    /** Required when [ExportScope.needsDeck]. */
    val deckId: String? = null,
    /** Required when [ExportScope.needsRound]. */
    val roundId: String? = null,
)
