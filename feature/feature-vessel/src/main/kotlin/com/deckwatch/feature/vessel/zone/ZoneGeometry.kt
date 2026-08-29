package com.deckwatch.feature.vessel.zone

import com.deckwatch.core.model.PlanPoint

/** A zone rectangle in the normalised 0..1 plan coordinate space of §6.3. */
data class ZoneRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * The **list-mode zone editor**.
 *
 * `ZoneEntity.polygon` is a free polygon (§6.4) and free-hand drawing belongs on the deck canvas,
 * which arrives with `feature-deckview`. Until then a zone has to be creatable without any
 * graphics at all — §7.1C is explicit that no function may be reachable only through the canvas.
 *
 * So here a zone is a **rectangle set by four sliders** (left / top / right / bottom, each 0..1)
 * and stored as an ordinary four-point polygon, clockwise from the top-left corner. That is a
 * real polygon in the schema, not a special case: when the canvas editor lands it can reshape
 * these zones point by point with no migration, and a zone drawn on the canvas round-trips
 * through this editor as its bounding box.
 */
object ZoneGeometry {

    /** Smallest edge a zone may have, so a slider slip cannot produce a zero-area zone. */
    const val MIN_SIZE: Float = 0.02f

    /** A sensible starting rectangle: the forward third of the deck, inset from the shell. */
    val Default: ZoneRect = ZoneRect(left = 0.15f, top = 0.10f, right = 0.85f, bottom = 0.40f)

    /**
     * Orders the edges, clamps them into 0..1 and guarantees [MIN_SIZE] on both axes.
     * `left`/`right` and `top`/`bottom` may arrive the wrong way round — a slider drag crosses.
     */
    fun normalise(rect: ZoneRect): ZoneRect {
        val (left, right) = orderedEdge(rect.left, rect.right)
        val (top, bottom) = orderedEdge(rect.top, rect.bottom)
        return ZoneRect(left = left, top = top, right = right, bottom = bottom)
    }

    /** The four-point polygon stored on the zone, clockwise from the top-left corner. */
    fun rectToPolygon(rect: ZoneRect): List<PlanPoint> {
        val safe = normalise(rect)
        return listOf(
            PlanPoint(safe.left, safe.top),
            PlanPoint(safe.right, safe.top),
            PlanPoint(safe.right, safe.bottom),
            PlanPoint(safe.left, safe.bottom),
        )
    }

    /**
     * The bounding box of an arbitrary polygon. A rectangle round-trips exactly; a polygon drawn
     * later on the canvas degrades to its bounds here rather than being refused.
     */
    fun polygonToRect(polygon: List<PlanPoint>): ZoneRect {
        if (polygon.isEmpty()) return Default
        return normalise(
            ZoneRect(
                left = polygon.minOf { it.x },
                top = polygon.minOf { it.y },
                right = polygon.maxOf { it.x },
                bottom = polygon.maxOf { it.y },
            ),
        )
    }

    private fun orderedEdge(a: Float, b: Float): Pair<Float, Float> {
        val low = minOf(a, b).coerceIn(0f, 1f - MIN_SIZE)
        val high = maxOf(a, b).coerceIn(low + MIN_SIZE, 1f)
        return low to high
    }
}
