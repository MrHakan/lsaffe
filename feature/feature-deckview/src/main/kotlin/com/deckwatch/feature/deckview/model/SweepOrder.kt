package com.deckwatch.feature.deckview.model

import com.deckwatch.core.model.ConditionGrade

/**
 * Sweep mode's walking order — §7.3.
 *
 * The officer walks one deck and grades item after item without the sheet closing. The order is the
 * order the markers are laid out fore-to-aft (`posY`, then `posX`, then id for stability), which is
 * how the deck is physically walked, and it is the same order [RenderModelAssembler] already sorts
 * markers into.
 *
 * An item is a *candidate* while it is still `NOT_CHECKED` **and** has not been graded during this
 * sweep. Grading an item during the sweep therefore takes it out of the running even if the officer
 * grades it `NOT_CHECKED`-equivalent later, which is what stops the sweep looping for ever.
 */
object SweepOrder {

    /** The markers of one deck in walking order. */
    fun order(markers: List<MarkerNode>): List<MarkerNode> =
        markers.sortedWith(compareBy({ it.position.y }, { it.position.x }, { it.equipmentId }))

    /** True when [marker] still needs grading in a sweep that has already covered [gradedInSweep]. */
    fun isCandidate(marker: MarkerNode, gradedInSweep: Set<String>): Boolean =
        marker.condition == ConditionGrade.NOT_CHECKED && marker.equipmentId !in gradedInSweep

    /** The first item a sweep of this deck should open. */
    fun first(markers: List<MarkerNode>, gradedInSweep: Set<String> = emptySet()): String? =
        order(markers).firstOrNull { isCandidate(it, gradedInSweep) }?.equipmentId

    /**
     * The next item after [currentId], searching forward from it and wrapping once.
     *
     * Returns null when nothing on the deck is left to grade — the caller then finishes the round
     * (§7.3: "finishing … sets completedAt and counts").
     */
    fun next(
        markers: List<MarkerNode>,
        gradedInSweep: Set<String>,
        currentId: String?,
    ): String? {
        val ordered = order(markers)
        if (ordered.isEmpty()) return null
        val start = ordered.indexOfFirst { it.equipmentId == currentId }
        if (start < 0) return ordered.firstOrNull { isCandidate(it, gradedInSweep) }?.equipmentId
        for (step in 1..ordered.size) {
            val candidate = ordered[(start + step) % ordered.size]
            if (isCandidate(candidate, gradedInSweep)) return candidate.equipmentId
        }
        return null
    }
}
