package com.deckwatch.data.repository

/**
 * Splits a ship's tag into the part that repeats and the number that increments — §7.5.
 *
 * `"FE-UD-07"` becomes prefix `"FE-UD-"`, number `7`, width `2`, so the next copies read
 * `FE-UD-08`, `FE-UD-09`, `FE-UD-10` and keep the ship's own zero padding. A tag with no trailing
 * digits (`"Fire blanket"`) gets `"Fire blanket-"` as its prefix and starts at width 1, so
 * duplicating it produces `Fire blanket-2`, `Fire blanket-3`, … rather than silently colliding.
 */
internal data class TagPattern(
    val prefix: String,
    val number: Int?,
    val width: Int,
) {
    /** Renders [value] with the pattern's zero padding. */
    fun render(value: Int): String = prefix + value.toString().padStart(width, '0')

    companion object {
        fun of(tag: String): TagPattern {
            val digits = tag.takeLastWhile { it.isDigit() }
            return if (digits.isEmpty()) {
                TagPattern(prefix = "$tag-", number = null, width = 1)
            } else {
                TagPattern(
                    prefix = tag.dropLast(digits.length),
                    number = digits.toIntOrNull(),
                    width = digits.length,
                )
            }
        }
    }
}
