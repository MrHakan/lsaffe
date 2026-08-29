package com.deckwatch.feature.equipment

/**
 * Tag auto-numbering — §7.5 step 3.
 *
 * A tag is the ship's own identifier for the item and is the one field the officer must fill, so the
 * app suggests one it can defend: `PREFIX-DECK-NN`, where
 * * `PREFIX` is [com.deckwatch.core.model.EquipmentType.defaultTagPrefix] from the catalogue
 *   (`FE`, `LB`, `LR`, …),
 * * `DECK` is the deck's `shortCode` (§6.2) upper-cased — omitted entirely when the item is being
 *   added off-plan, giving `PREFIX-NN`,
 * * `NN` is the next free number for that prefix on the vessel, from
 *   [com.deckwatch.core.common.repository.EquipmentRepository.nextTagNumber], zero-padded to two
 *   digits and growing beyond two naturally (`FE-UD-100`).
 *
 * The prefix handed to `nextTagNumber` **includes the trailing hyphen** (`FE-UD-`), so numbering is
 * per prefix *and* per deck: the eighth extinguisher on the Upper Deck is `FE-UD-08` even when
 * `FE-A-12` already exists.
 */
internal object TagSuggestion {

    /** Minimum width of the numeric suffix. */
    private const val PAD_WIDTH = 2

    /** The tag prefix, including its trailing separator — the string to number against. */
    fun prefix(defaultTagPrefix: String, deckShortCode: String?): String {
        val base = defaultTagPrefix.trim().ifEmpty { "EQ" }
        val deck = deckShortCode?.trim()?.uppercase().orEmpty()
        return if (deck.isEmpty()) "$base-" else "$base-$deck-"
    }

    /** `FE-UD-` + 8 -> `FE-UD-08`. */
    fun format(prefix: String, number: Int): String =
        prefix + number.coerceAtLeast(1).toString().padStart(PAD_WIDTH, '0')

    /** The whole suggestion in one call. */
    fun suggest(defaultTagPrefix: String, deckShortCode: String?, nextNumber: Int): String =
        format(prefix(defaultTagPrefix, deckShortCode), nextNumber)

    /**
     * The tag [by] places after [tag], for duplicate ×N — §7.5.
     *
     * The trailing digit group is incremented and its width preserved (`FE-UD-08` + 2 ->
     * `FE-UD-10`), widening only when it must (`FE-UD-99` + 1 -> `FE-UD-100`). A tag with no
     * trailing digits gets a `-n` suffix (`LB PORT` + 1 -> `LB PORT-2`) rather than being mangled:
     * ships name equipment in ways no numbering scheme predicts.
     */
    fun increment(tag: String, by: Int): String {
        if (by <= 0) return tag
        val digits = tag.takeLastWhile { it.isDigit() }
        if (digits.isEmpty()) return "$tag-${by + 1}"
        val stem = tag.dropLast(digits.length)
        val next = (digits.toLongOrNull() ?: return "$tag-${by + 1}") + by
        return stem + next.toString().padStart(digits.length, '0')
    }
}
