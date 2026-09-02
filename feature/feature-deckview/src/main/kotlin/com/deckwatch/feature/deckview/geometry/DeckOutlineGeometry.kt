package com.deckwatch.feature.deckview.geometry

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape

/**
 * The outline of a [DeckPlan] as a closed polygon in plan space (0..1 on both axes) — §6.3.
 *
 * This is deliberately the *same* hull mathematics as `feature-vessel`'s `DeckPlanOutline`, so a
 * list-mode thumbnail and the 2.5D canvas draw the same ship. It is reproduced here rather than
 * depended upon because `feature-vessel` must not depend on `feature-deckview`, and the canvas needs
 * a polygon (for point-in-polygon hit-testing and for projection) where the thumbnail needs a
 * Compose `Path`. The curved sections are the same quadratic Béziers, sampled at
 * [CURVE_SAMPLES] points each.
 */
object DeckOutlineGeometry {

    /** How many straight segments each quadratic curve of the hull is flattened into. */
    const val CURVE_SAMPLES: Int = 10

    /**
     * The closed outline of [plan] in plan space. The first point is not repeated at the end.
     *
     * A `CUSTOM_POLYGON` with fewer than three points falls back to the plan's rectangle, so a
     * half-drawn custom outline still renders something the officer can tap.
     */
    fun polygon(plan: DeckPlan): List<Vec2> {
        val breadth = plan.breadthRatio.coerceIn(MIN_RATIO, 1f)
        val length = plan.lengthRatio.coerceIn(MIN_RATIO, 1f)
        val left = (1f - breadth) / 2f
        val top = (1f - length) / 2f
        val right = left + breadth
        val bottom = top + length

        return when (plan.shape) {
            PlanShape.RECTANGLE -> rectangle(left, top, right, bottom)

            PlanShape.L_SHAPE -> {
                val notchX = left + breadth * L_NOTCH_FRACTION
                val notchY = top + length * L_NOTCH_FRACTION
                listOf(
                    Vec2(left, top),
                    Vec2(notchX, top),
                    Vec2(notchX, notchY),
                    Vec2(right, notchY),
                    Vec2(right, bottom),
                    Vec2(left, bottom),
                )
            }

            PlanShape.CUSTOM_POLYGON ->
                if (plan.polygon.size >= MIN_POLYGON_POINTS) {
                    plan.polygon.map { Vec2(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
                } else {
                    rectangle(left, top, right, bottom)
                }

            PlanShape.SHIP_HULL -> shipHull(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                bowSharpness = plan.bowSharpness.coerceIn(0f, 1f),
                sternRounding = plan.sternRounding.coerceIn(0f, 1f),
                bowAtTop = plan.bowAtTop,
            )
        }
    }

    /** A cache key for a plan's projected path — the `planHash` of §7.2's path cache. */
    fun planHash(plan: DeckPlan): Int {
        var hash = plan.shape.ordinal
        hash = HASH_MULTIPLIER * hash + plan.lengthRatio.toRawBits()
        hash = HASH_MULTIPLIER * hash + plan.breadthRatio.toRawBits()
        hash = HASH_MULTIPLIER * hash + plan.bowSharpness.toRawBits()
        hash = HASH_MULTIPLIER * hash + plan.sternRounding.toRawBits()
        hash = HASH_MULTIPLIER * hash + if (plan.bowAtTop) 1 else 0
        for (point in plan.polygon) {
            hash = HASH_MULTIPLIER * hash + point.x.toRawBits()
            hash = HASH_MULTIPLIER * hash + point.y.toRawBits()
        }
        return hash
    }

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float): List<Vec2> =
        listOf(Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom))

    /**
     * A rectangle with a bow taper and a rounded stern — §6.3's parametric `SHIP_HULL`.
     *
     * `bowSharpness` 0 leaves a blunt, full-breadth bow (a barge); 1 draws a fine point. The taper
     * runs over the forward [BOW_MIN_FRACTION]..[BOW_MAX_FRACTION] of the deck length, and the stern
     * corners are radiused by `sternRounding × half-breadth`. Built bow-up, then mirrored about the
     * plan box's centre line when [bowAtTop] is false.
     */
    @Suppress("LongParameterList") // Mirrors the plan's own parameters; the caller passes a DeckPlan.
    private fun shipHull(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        bowSharpness: Float,
        sternRounding: Float,
        bowAtTop: Boolean,
    ): List<Vec2> {
        val breadth = right - left
        val length = bottom - top
        val halfBreadth = breadth / 2f
        val centreX = left + halfBreadth

        val bowRun = length * (BOW_MIN_FRACTION + (BOW_MAX_FRACTION - BOW_MIN_FRACTION) * bowSharpness)
        val bowHalfWidth = halfBreadth * (1f - bowSharpness * BOW_TIP_PINCH)
        val sternRadius = (halfBreadth * sternRounding).coerceAtMost(length / 2f)

        val shoulderY = top + bowRun
        val sternShoulderY = bottom - sternRadius
        val shoulderControlY = top + bowRun * SHOULDER_PULL

        val points = ArrayList<Vec2>(CURVE_SAMPLES * CURVE_COUNT + STRAIGHT_POINTS)
        val portBow = Vec2(centreX - bowHalfWidth, top)
        val starboardBow = Vec2(centreX + bowHalfWidth, top)

        points += portBow
        // Bow crown, port shoulder to starboard shoulder.
        points += quadratic(portBow, Vec2(centreX, top - bowRun * BOW_CROWN), starboardBow)
        // Starboard shoulder into the parallel body.
        points += quadratic(starboardBow, Vec2(right, shoulderControlY), Vec2(right, shoulderY))
        points += Vec2(right, sternShoulderY)
        if (sternRadius > 0f) {
            points += quadratic(
                Vec2(right, sternShoulderY),
                Vec2(right, bottom),
                Vec2(right - sternRadius, bottom),
            )
            points += Vec2(left + sternRadius, bottom)
            points += quadratic(
                Vec2(left + sternRadius, bottom),
                Vec2(left, bottom),
                Vec2(left, sternShoulderY),
            )
        } else {
            points += Vec2(right, bottom)
            points += Vec2(left, bottom)
        }
        points += Vec2(left, shoulderY)
        points += quadratic(Vec2(left, shoulderY), Vec2(left, shoulderControlY), portBow)

        return if (bowAtTop) points else points.map { Vec2(it.x, top + bottom - it.y) }
    }

    /** Samples a quadratic Bézier, excluding `start` and including `end`. */
    private fun quadratic(start: Vec2, control: Vec2, end: Vec2): List<Vec2> =
        (1..CURVE_SAMPLES).map { step ->
            val t = step.toFloat() / CURVE_SAMPLES
            val inv = 1f - t
            Vec2(
                x = inv * inv * start.x + 2f * inv * t * control.x + t * t * end.x,
                y = inv * inv * start.y + 2f * inv * t * control.y + t * t * end.y,
            )
        }

    private const val MIN_RATIO = 0.05f
    private const val MIN_POLYGON_POINTS = 3
    private const val L_NOTCH_FRACTION = 0.55f
    private const val BOW_MIN_FRACTION = 0.10f
    private const val BOW_MAX_FRACTION = 0.34f
    private const val BOW_TIP_PINCH = 0.92f
    private const val BOW_CROWN = 0.22f
    private const val SHOULDER_PULL = 0.45f
    private const val CURVE_COUNT = 5
    private const val STRAIGHT_POINTS = 4
    private const val HASH_MULTIPLIER = 31
}

/** Plan-space polygon helpers shared by hit-testing, zone rendering and the drop test of §7.2. */
object Polygons {

    /**
     * Ray-casting point-in-polygon. Points exactly on an edge are unspecified — that is fine for a
     * touch test where the nearest pixel decides anyway.
     */
    fun contains(polygon: List<Vec2>, point: Vec2): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > point.y) != (b.y > point.y)) {
                val t = (point.y - a.y) / (b.y - a.y)
                if (point.x < a.x + t * (b.x - a.x)) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** The polygon's arithmetic centre — where an aggregated zone dot sits (§7.2 LOD). */
    fun centroid(polygon: List<Vec2>): Vec2 {
        if (polygon.isEmpty()) return Vec2(IsoProjection.PLAN_CENTRE, IsoProjection.PLAN_CENTRE)
        var sx = 0f
        var sy = 0f
        for (point in polygon) {
            sx += point.x
            sy += point.y
        }
        return Vec2(sx / polygon.size, sy / polygon.size)
    }

    /** Converts a stored [PlanPoint] polygon (zones — §6.4) into plan-space vectors. */
    fun of(points: List<PlanPoint>): List<Vec2> = points.map { Vec2(it.x, it.y) }
}
