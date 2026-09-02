package com.deckwatch.feature.deckview.geometry

import com.deckwatch.core.model.Deck

/** A marker as the hit-tester sees it: an id and where it landed on screen. */
data class ScreenMarker(val id: String, val centre: Vec2)

/** A deck as the hit-tester sees it: an id, its rank in the stack and its projected outline. */
data class ScreenDeck(val id: String, val levelZ: Int, val outline: List<Vec2>)

/** What a tap landed on — §7.2's gesture table resolves markers before deck surfaces. */
sealed interface HitTarget {
    data class Marker(val equipmentId: String, val deckId: String) : HitTarget
    data class Surface(val deckId: String, val plan: Vec2) : HitTarget
    data object None : HitTarget
}

/**
 * Resolving a touch against the projected stack — §7.2.
 *
 * Priority is fixed by the gesture table: a marker always wins over the deck surface under it, and
 * among decks the topmost (highest rank) wins, because that is the one drawn last.
 */
object HitTesting {

    /**
     * The nearest marker to [point] within [radiusPx], or null.
     *
     * Nearest-within-radius rather than first-containing, so two markers a few pixels apart still
     * resolve predictably under a gloved thumb (C6).
     */
    fun nearestMarker(markers: List<ScreenMarker>, point: Vec2, radiusPx: Float): ScreenMarker? {
        if (radiusPx <= 0f) return null
        var best: ScreenMarker? = null
        var bestDistance = radiusPx * radiusPx
        for (marker in markers) {
            val dx = marker.centre.x - point.x
            val dy = marker.centre.y - point.y
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                bestDistance = distance
                best = marker
            }
        }
        return best
    }

    /** The topmost deck whose projected outline contains [point], or null. */
    fun deckAt(decks: List<ScreenDeck>, point: Vec2): ScreenDeck? =
        decks.sortedByDescending { it.levelZ }.firstOrNull { Polygons.contains(it.outline, point) }
}

/** Ordering the stack — §6.2's `levelIndex` rule and §7.2's draw order. */
object DeckStackOrder {

    /** A deck with the *rank* the renderer projects it at. */
    data class Ranked(val deck: Deck, val levelZ: Int)

    /**
     * Decks in painter's order: bottom first, so the stack is drawn back-to-front (§7.2).
     *
     * `levelZ` is the deck's **rank index**, not its `levelIndex`. §6.2 leaves gaps of 10 between
     * levels so that decks can be inserted without a renumbering migration; projecting the raw index
     * would fan a two-deck vessel twenty deck-heights apart.
     */
    fun bottomFirst(decks: List<Deck>): List<Ranked> =
        decks.sortedWith(compareBy({ it.levelIndex }, { it.id }))
            .mapIndexed { rank, deck -> Ranked(deck, rank) }

    /** The same ranking, highest deck first — the order the spine and list-style chrome read in. */
    fun topFirst(decks: List<Deck>): List<Ranked> = bottomFirst(decks).asReversed()
}

/** Zoom clamps — §7.2's gesture table. */
object ZoomLimits {
    val Stack: ClosedFloatingPointRange<Float> = 0.4f..4f
    val Deck: ClosedFloatingPointRange<Float> = 0.4f..6f

    fun clampStack(zoom: Float): Float = zoom.coerceIn(Stack)
    fun clampDeck(zoom: Float): Float = zoom.coerceIn(Deck)

    /** Clamps for [deckMode] — deck mode allows a closer look for precise placement (§7.1B). */
    fun clamp(zoom: Float, deckMode: Boolean): Float =
        if (deckMode) clampDeck(zoom) else clampStack(zoom)
}

/** The optional placement grid of §7.2 — `UserPreferences.gridSnapEnabled`. */
object GridSnap {

    /** Twenty divisions across the plan: a 5 % step, fine enough to line extinguishers up. */
    const val DEFAULT_DIVISIONS: Int = 20

    /** Snaps one normalised coordinate to the nearest grid line and clamps it to the plan. */
    fun snapCoordinate(value: Float, divisions: Int = DEFAULT_DIVISIONS): Float {
        if (divisions <= 0) return value.coerceIn(0f, 1f)
        val step = 1f / divisions
        return (Math.round(value / step) * step).coerceIn(0f, 1f)
    }

    /** Snaps a plan point, or passes it through unchanged when [enabled] is false. */
    fun snap(point: Vec2, enabled: Boolean, divisions: Int = DEFAULT_DIVISIONS): Vec2 =
        if (!enabled) {
            Vec2(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        } else {
            Vec2(snapCoordinate(point.x, divisions), snapCoordinate(point.y, divisions))
        }
}
