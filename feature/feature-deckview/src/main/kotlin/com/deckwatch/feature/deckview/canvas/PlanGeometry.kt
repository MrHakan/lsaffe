package com.deckwatch.feature.deckview.canvas

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The 2.5D projection of MASTER_PROMPT §7.2, and the deck outlines of §6.3.
 *
 * Everything here is pure arithmetic on the normalised 0..1 plan space, deliberately kept out of
 * the composable: placement has to be exact. An officer long-presses a spot on a tilted deck and
 * the item must land on *that* spot, which means the projection has to be invertible and the
 * inverse has to be the real one, not an approximation that drifts at the edges.
 */
object PlanGeometry {

    /**
     * Projects a plan point to canvas space, both in 0..1.
     *
     * The transform is a vertical squash plus a horizontal shear — the flattening the eye reads as
     * "seen from a raised bow" — rather than a full perspective. It has an exact inverse
     * ([unproject]), which perspective does not, and at 0° it collapses to the flat plan the spec
     * asks for as the bottom of the angle range.
     */
    fun project(x: Float, y: Float, angleDeg: Float): PlanPoint {
        val squash = squash(angleDeg)
        val shear = shear(angleDeg)
        val dy = y - HALF
        return PlanPoint(
            x = (x - HALF) + dy * shear + HALF,
            y = dy * squash + HALF,
        )
    }

    /**
     * The exact inverse of [project]: canvas space back to plan space.
     *
     * Solved in the order the forward transform applies it — y first, because the sheared x
     * depends on it. Getting that order wrong is the classic way a marker lands a few percent off
     * the finger and nobody can say why.
     */
    fun unproject(x: Float, y: Float, angleDeg: Float): PlanPoint {
        val squash = squash(angleDeg)
        val shear = shear(angleDeg)
        val dy = (y - HALF) / squash
        return PlanPoint(
            x = (x - HALF) - dy * shear + HALF,
            y = dy + HALF,
        )
    }

    /**
     * The outline of a deck in plan space, closed implicitly (the last point joins the first).
     *
     * Ratios narrow the outline within the unit square rather than scaling the canvas, so a short
     * wide deck and a long narrow one sit in the same frame and read as different shapes — which is
     * the whole point of the presets in §6.3.
     */
    fun outline(plan: DeckPlan): List<PlanPoint> {
        val halfLength = (plan.lengthRatio.coerceIn(MIN_RATIO, 1f)) / 2f
        val halfBreadth = (plan.breadthRatio.coerceIn(MIN_RATIO, 1f)) / 2f
        val left = HALF - halfBreadth
        val right = HALF + halfBreadth
        val top = HALF - halfLength
        val bottom = HALF + halfLength

        return when (plan.shape) {
            PlanShape.RECTANGLE -> listOf(
                PlanPoint(left, top),
                PlanPoint(right, top),
                PlanPoint(right, bottom),
                PlanPoint(left, bottom),
            )

            PlanShape.SHIP_HULL -> hull(plan, left, right, top, bottom)

            PlanShape.L_SHAPE -> {
                val midX = left + (right - left) * L_SHAPE_FRACTION
                val midY = top + (bottom - top) * L_SHAPE_FRACTION
                listOf(
                    PlanPoint(left, top),
                    PlanPoint(midX, top),
                    PlanPoint(midX, midY),
                    PlanPoint(right, midY),
                    PlanPoint(right, bottom),
                    PlanPoint(left, bottom),
                )
            }

            // An authored polygon is used as drawn; falling back to the rectangle keeps a deck
            // whose polygon was lost in an import from rendering as nothing at all.
            PlanShape.CUSTOM_POLYGON -> plan.polygon.takeIf { it.size >= MIN_POLYGON_POINTS }
                ?: outline(plan.copy(shape = PlanShape.RECTANGLE))
        }
    }

    /**
     * A hull: a pointed bow, parallel sides, a rounded stern.
     *
     * `bowSharpness` moves the shoulder — 0 is a barge, 1 a fine entry — and `sternRounding` cuts
     * the after corners. `bowAtTop` flips the whole thing, because a deck plan is drawn the way
     * the officer walks it.
     */
    private fun hull(plan: DeckPlan, left: Float, right: Float, top: Float, bottom: Float): List<PlanPoint> {
        val sharpness = plan.bowSharpness.coerceIn(0f, 1f)
        val rounding = plan.sternRounding.coerceIn(0f, 1f)
        val length = bottom - top
        val breadth = right - left
        val shoulder = top + length * (BOW_MIN_FRACTION + sharpness * BOW_SPAN_FRACTION)
        val quarter = bottom - length * rounding * STERN_SPAN_FRACTION
        val inset = breadth * rounding * STERN_INSET_FRACTION

        val bowFirst = listOf(
            PlanPoint(HALF, top),
            PlanPoint(right, shoulder),
            PlanPoint(right, quarter),
            PlanPoint(right - inset, bottom),
            PlanPoint(left + inset, bottom),
            PlanPoint(left, quarter),
            PlanPoint(left, shoulder),
        )
        return if (plan.bowAtTop) bowFirst else bowFirst.map { PlanPoint(it.x, top + bottom - it.y) }
    }

    /**
     * Even-odd ray cast: is [point] inside [polygon]?
     *
     * Used to work out which zone the officer just dropped an item into. A zone with fewer than
     * three points encloses nothing, so it contains nothing — an empty or half-drawn zone must not
     * silently claim every drop on the deck.
     */
    fun contains(polygon: List<PlanPoint>, point: PlanPoint): Boolean {
        if (polygon.size < MIN_POLYGON_POINTS) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val straddles = (a.y > point.y) != (b.y > point.y)
            if (straddles) {
                val t = (point.y - a.y) / (b.y - a.y)
                if (point.x < a.x + t * (b.x - a.x)) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Squeeze applied to the fore-and-aft axis; 1 at 0° (flat plan). */
    private fun squash(angleDeg: Float): Float =
        cos(angleDeg.coerceIn(MIN_ANGLE, MAX_ANGLE).toRadians()).toFloat().coerceAtLeast(MIN_SQUASH)

    /** Sideways lean; 0 at 0°. */
    private fun shear(angleDeg: Float): Float =
        sin(angleDeg.coerceIn(MIN_ANGLE, MAX_ANGLE).toRadians()).toFloat() * SHEAR_SCALE

    private fun Float.toRadians(): Double = this * Math.PI / HALF_TURN_DEG

    /** Two plan points closer than this on both axes are the same spot to a fingertip. */
    fun near(a: PlanPoint, b: PlanPoint, tolerance: Float): Boolean =
        abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance

    private const val HALF = 0.5f
    private const val HALF_TURN_DEG = 180.0
    private const val MIN_ANGLE = 0f
    private const val MAX_ANGLE = 35f

    /** Never fully flat: a zero squash would make the inverse divide by zero. */
    private const val MIN_SQUASH = 0.2f

    /** How far the lean goes at the top of the angle range; a full 1.0 would read as a fall-over. */
    private const val SHEAR_SCALE = 0.6f

    private const val MIN_RATIO = 0.1f
    private const val MIN_POLYGON_POINTS = 3
    private const val L_SHAPE_FRACTION = 0.55f
    private const val BOW_MIN_FRACTION = 0.08f
    private const val BOW_SPAN_FRACTION = 0.22f
    private const val STERN_SPAN_FRACTION = 0.12f
    private const val STERN_INSET_FRACTION = 0.18f
}
