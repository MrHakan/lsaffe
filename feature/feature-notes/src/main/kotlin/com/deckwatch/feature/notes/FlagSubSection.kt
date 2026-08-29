package com.deckwatch.feature.notes

import com.deckwatch.core.model.RegulationCard

/**
 * The three flag Administrations the FLAG section splits into — §8.1.
 *
 * [code] is the canonical key used by [RegulationCard.flagNotes] throughout the seed content
 * (the card mock-up in §8.2 shows `RMI · LIB · PAN`).
 */
enum class FlagSubSection(val code: String) {
    RMI("RMI"),
    LIBERIA("LIB"),
    PANAMA("PAN"),
    ;

    companion object {
        /**
         * Canonical code or full country name, case-insensitive. Deliberately strict:
         * two-letter aliases (`MH`, `LR`, `PA`) are *not* accepted because they collide with
         * class-society and other codes that appear in the same strings — a wrong bucket is
         * worse than an unassigned one.
         */
        fun fromCode(raw: String): FlagSubSection? = when (raw.trim().uppercase()) {
            "RMI", "MARSHALL", "MARSHALLISLANDS", "MARSHALL ISLANDS" -> RMI
            "LIB", "LIBERIA" -> LIBERIA
            "PAN", "PANAMA" -> PANAMA
            else -> null
        }
    }
}

/**
 * Derive which Administration a FLAG-section card belongs to — the rule behind the
 * RMI / Liberia / Panama sub-lists.
 *
 * The bundled content is authored by hand and cannot be relied on to use one single convention,
 * so three independent signals are tried in order of how strong the evidence is. The first signal
 * that resolves to **exactly one** Administration wins; a signal that points at two or more is
 * discarded rather than guessed at.
 *
 * 1. **`flagNotes` keys.** A FLAG card about one Administration carries that one key. A card
 *    carrying all three (a comparison card) falls through to the next signal.
 * 2. **`refKey` tokens.** The seed convention is `FLAG_<CODE>_<notice>`, e.g. `FLAG_RMI_2_011_37`.
 *    The key is split on `_ - . space` and each token is matched against
 *    [FlagSubSection.Companion.fromCode].
 * 3. **Notice numbering** in `citation` and `sourceRef`. The three registries number their
 *    notices distinctively, so the number alone identifies the Administration:
 *    RMI Marine Notices `2-011-nn`, Liberia `SAF-nnn` / `FIR-nnn`, Panama `MMC-nnn`.
 *
 * Returns `null` when nothing matches. Such a card is still listed — under "Other flag notices" —
 * because a card must never disappear just because a heuristic failed (§8.5).
 */
fun RegulationCard.flagSubSection(): FlagSubSection? =
    fromFlagNoteKeys(flagNotes.keys)
        ?: fromRefKeyTokens(refKey)
        ?: fromNoticeNumbering(citation, sourceRef, revisionNote)

private fun fromFlagNoteKeys(keys: Set<String>): FlagSubSection? =
    keys.mapNotNull(FlagSubSection::fromCode).distinct().singleOrNull()

private fun fromRefKeyTokens(refKey: String): FlagSubSection? =
    refKey.split('_', '-', '.', ' ')
        .mapNotNull(FlagSubSection::fromCode)
        .distinct()
        .singleOrNull()

private fun fromNoticeNumbering(vararg fields: String): FlagSubSection? {
    val haystack = fields.joinToString(separator = " ").uppercase()
    val matches = buildList {
        if (RmiNoticeNumber.containsMatchIn(haystack)) add(FlagSubSection.RMI)
        if (LiberiaNoticeNumber.containsMatchIn(haystack)) add(FlagSubSection.LIBERIA)
        if (PanamaNoticeNumber.containsMatchIn(haystack)) add(FlagSubSection.PANAMA)
    }
    return matches.singleOrNull()
}

/** RMI Marine Notice numbering, e.g. "MN 2-011-37". */
private val RmiNoticeNumber = Regex("""\b2-0\d{2}-\d{1,2}\b""")

/** Liberia Marine Notice numbering, e.g. "SAF-005", "FIR-001". */
private val LiberiaNoticeNumber = Regex("""\b(SAF|FIR)-\d{3}\b""")

/** Panama Merchant Marine Circular numbering, e.g. "MMC-281". */
private val PanamaNoticeNumber = Regex("""\bMMC-\d{2,4}\b""")
