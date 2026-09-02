package com.deckwatch.feature.report

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import java.util.Locale

/**
 * The deck outline as an SVG path — §13.4 ("deck plans re-rendered as inline SVG … matching the
 * app").
 *
 * This is a **deliberate copy** of the geometry in
 * `feature-vessel/…/common/DeckOutline.kt`, not a dependency on it. `feature-report` must not
 * depend on `feature-vessel`: the exporter has no business pulling in another feature's Compose
 * canvases, and a report has to render identically whether or not that module is on the classpath.
 * The two therefore share the *rules* of §6.3 and the constants below rather than code.
 *
 * **If the app's outline maths changes, this file changes with it** — `DeckOutlineParityTest`
 * pins the constants so the divergence is caught rather than discovered on a printed sheet.
 *
 * The output is a flat, top-down plan. Isometric (§7.2) is deliberately not attempted here: a
 * printed deck sheet is measured against, and a projected plan is the wrong thing to hold up to a
 * bulkhead. §13.3 lists the flat plan as the requirement and the isometric as optional.
 */
internal object DeckSvg {

    /** Outline of [plan] as an SVG `d` attribute, scaled into a [width] x [height] viewBox. */
    fun outlinePath(plan: DeckPlan, width: Float, height: Float): String {
        val breadth = width * plan.breadthRatio.coerceIn(MIN_RATIO, 1f)
        val length = height * plan.lengthRatio.coerceIn(MIN_RATIO, 1f)
        val left = (width - breadth) / 2f
        val top = (height - length) / 2f
        val right = left + breadth
        val bottom = top + length

        return when (plan.shape) {
            PlanShape.RECTANGLE -> rectangle(left, top, right, bottom)

            PlanShape.L_SHAPE -> lShape(left, top, right, bottom, breadth, length)

            PlanShape.CUSTOM_POLYGON ->
                if (plan.polygon.size >= MIN_POLYGON_POINTS) {
                    polygon(plan.polygon, width, height)
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

    /**
     * Where a marker at normalised [posX] / [posY] sits in a [width] x [height] viewBox.
     *
     * Equipment coordinates are "0..1 within the deck plan" (§6.5), i.e. the plan *box*, which is
     * also what the app's canvas uses — so an item does not move when a deck's length or breadth
     * ratio is edited.
     */
    fun markerPoint(posX: Float, posY: Float, width: Float, height: Float): Pair<Float, Float> =
        posX.coerceIn(0f, 1f) * width to posY.coerceIn(0f, 1f) * height

    // ------------------------------------------------------------------ shapes

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float): String =
        path {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }

    private fun lShape(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        breadth: Float,
        length: Float,
    ): String = path {
        val notchX = left + breadth * L_NOTCH_FRACTION
        val notchY = top + length * L_NOTCH_FRACTION
        moveTo(left, top)
        lineTo(notchX, top)
        lineTo(notchX, notchY)
        lineTo(right, notchY)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }

    private fun polygon(points: List<PlanPoint>, width: Float, height: Float): String = path {
        points.forEachIndexed { index, point ->
            val x = point.x.coerceIn(0f, 1f) * width
            val y = point.y.coerceIn(0f, 1f) * height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /**
     * A rectangle with a bow taper and a rounded stern — the parametric `SHIP_HULL` of §6.3.
     *
     * `bowSharpness` 0 leaves a blunt, full-breadth bow (a barge); 1 draws a fine point. The taper
     * runs over the forward [BOW_MIN_FRACTION]..[BOW_MAX_FRACTION] of the deck length, and the
     * stern corners are radiused by `sternRounding` x half-breadth. Always built bow-up, then
     * mirrored about the plan box's centre line when [bowAtTop] is false — the same reflection the
     * app applies with a matrix, expressed here as `y -> top + bottom - y`.
     */
    private fun shipHull(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        bowSharpness: Float,
        sternRounding: Float,
        bowAtTop: Boolean,
    ): String {
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

        // The mirror of the app's Matrix { translate(y = top + bottom); scale(y = -1f) }.
        val flip: (Float) -> Float = if (bowAtTop) {
            { it }
        } else {
            { top + bottom - it }
        }

        return path(flip) {
            moveTo(centreX - bowHalfWidth, top)
            quadTo(centreX, top - bowRun * BOW_CROWN, centreX + bowHalfWidth, top)
            quadTo(right, shoulderControlY, right, shoulderY)
            lineTo(right, sternShoulderY)
            if (sternRadius > 0f) {
                quadTo(right, bottom, right - sternRadius, bottom)
                lineTo(left + sternRadius, bottom)
                quadTo(left, bottom, left, sternShoulderY)
            } else {
                lineTo(right, bottom)
                lineTo(left, bottom)
            }
            lineTo(left, shoulderY)
            quadTo(left, shoulderControlY, centreX - bowHalfWidth, top)
            close()
        }
    }

    // ------------------------------------------------------------------ path builder

    private fun path(flipY: (Float) -> Float = { it }, build: PathBuilder.() -> Unit): String =
        PathBuilder(flipY).apply(build).toString()

    /** Accumulates an SVG `d` attribute, applying the optional vertical reflection as it goes. */
    private class PathBuilder(private val flipY: (Float) -> Float) {
        private val out = StringBuilder()

        fun moveTo(x: Float, y: Float) = append("M", x, y)
        fun lineTo(x: Float, y: Float) = append("L", x, y)

        fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
            if (out.isNotEmpty()) out.append(' ')
            out.append('Q').append(' ').append(num(cx)).append(' ').append(num(flipY(cy)))
                .append(' ').append(num(x)).append(' ').append(num(flipY(y)))
        }

        fun close() {
            if (out.isNotEmpty()) out.append(' ')
            out.append('Z')
        }

        private fun append(command: String, x: Float, y: Float) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(command).append(' ').append(num(x)).append(' ').append(num(flipY(y)))
        }

        override fun toString(): String = out.toString()
    }

    /** Two decimals, always with a `.` separator — an SVG path is not locale-aware. */
    fun num(value: Float): String {
        val rendered = String.format(Locale.ROOT, "%.2f", value)
        return if (rendered.endsWith(".00")) rendered.dropLast(3) else rendered.trimEnd('0').trimEnd('.')
    }

    // ---- Geometry constants — must stay identical to feature-vessel's DeckOutline.kt (§6.3).
    const val MIN_RATIO: Float = 0.05f
    const val MIN_POLYGON_POINTS: Int = 3
    const val L_NOTCH_FRACTION: Float = 0.55f
    const val BOW_MIN_FRACTION: Float = 0.10f
    const val BOW_MAX_FRACTION: Float = 0.34f
    const val BOW_TIP_PINCH: Float = 0.92f
    const val BOW_CROWN: Float = 0.22f
    const val SHOULDER_PULL: Float = 0.45f
}
