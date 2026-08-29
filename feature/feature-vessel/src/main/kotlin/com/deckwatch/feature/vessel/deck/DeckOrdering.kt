package com.deckwatch.feature.vessel.deck

import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Deck
import com.deckwatch.core.model.Equipment

/**
 * One gap between two adjacent decks in the stack, offered as an "insert between" affordance.
 *
 * The repository owns the `levelIndex` mechanic (§6.2) — this only works out *which* pairs the UI
 * should offer, and whether the midpoint the repository would pick actually exists.
 */
data class InsertSlot(
    val lowerLevelIndex: Int,
    val upperLevelIndex: Int,
    val enabled: Boolean,
)

/** Presentation-side ordering rules for the deck stack. No writes happen here. */
object DeckOrdering {

    /** The stack always renders sorted by `levelIndex` descending — highest deck at the top (§6.2). */
    fun sortedForStack(decks: List<Deck>): List<Deck> = decks.sortedByDescending { it.levelIndex }

    /**
     * `true` when there is an integer strictly between the two levels, i.e. when
     * `VesselRepository.insertDeckBetween` has somewhere to put the new deck. Adjacent indices
     * (a difference of one) have no room and need a renumber first, so the affordance is shown
     * disabled rather than hidden — an officer should see *why* they cannot insert there.
     */
    fun hasRoomBetween(lowerLevelIndex: Int, upperLevelIndex: Int): Boolean {
        val low = minOf(lowerLevelIndex, upperLevelIndex)
        val high = maxOf(lowerLevelIndex, upperLevelIndex)
        val midpoint = (low + high).floorDiv(2)
        return midpoint != low && midpoint != high
    }

    /** One slot per adjacent pair, in stack order (top pair first). */
    fun insertSlots(decks: List<Deck>): List<InsertSlot> =
        sortedForStack(decks).zipWithNext { upper, lower ->
            InsertSlot(
                lowerLevelIndex = lower.levelIndex,
                upperLevelIndex = upper.levelIndex,
                enabled = hasRoomBetween(lower.levelIndex, upper.levelIndex),
            )
        }

    /**
     * The worst *known* condition among [equipment].
     *
     * `NOT_CHECKED` scores below `OUT_OF_SERVICE` in [ConditionGrade], but it means "unknown",
     * not "worse" — a deck of ungraded items must not paint the same red as a deck with a
     * condemned lifeboat on it. So the worst known grade wins, and `NOT_CHECKED` is reported only
     * when nothing on the deck has been graded at all. Empty decks report `null`.
     */
    fun worstCondition(equipment: List<Equipment>): ConditionGrade? {
        if (equipment.isEmpty()) return null
        val graded = equipment.filter { it.condition != ConditionGrade.NOT_CHECKED }
        return graded.minByOrNull { it.condition.score }?.condition ?: ConditionGrade.NOT_CHECKED
    }
}
