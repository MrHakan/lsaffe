package com.deckwatch.feature.vessel.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape

/**
 * The mini plan preview drawn on the preset picker, the deck rows and the zone editor.
 *
 * This is deliberately a *small flat outline*, not the 2.5D renderer of §7.2 — that arrives with
 * `feature-deckview`. What lives here is the unit-space outline of a [DeckPlan] so list mode can
 * show the shape of a deck with none of the canvas machinery (§7.1C).
 *
 * `SHIP_HULL` is the parametric outline of §6.3: a rectangle with a bow taper driven by
 * [DeckPlan.bowSharpness] and a stern rounded by [DeckPlan.sternRounding], inset to
 * [DeckPlan.lengthRatio] x [DeckPlan.breadthRatio] of the box it is given.
 */
@Composable
fun DeckPlanOutline(
    plan: DeckPlan,
    modifier: Modifier = Modifier,
    fill: Color = Color.Unspecified,
    stroke: Color = Color.Unspecified,
    strokeWidth: Dp = 1.5.dp,
    zone: List<PlanPoint> = emptyList(),
    zoneColor: Color = Color.Unspecified,
) {
    Canvas(modifier = modifier) {
        val path = deckPlanPath(plan, size)
        if (fill.isSpecified) {
            drawPath(path = path, color = fill)
        }
        if (zone.size >= MIN_POLYGON_POINTS && zoneColor.isSpecified) {
            drawPath(path = polygonPath(zone, size), color = zoneColor)
        }
        if (stroke.isSpecified) {
            drawPath(path = path, color = stroke, style = Stroke(width = strokeWidth.toPx()))
        }
    }
}

/** A fixed-size convenience used by the preset picker tiles. */
@Composable
fun DeckPlanThumbnail(
    plan: DeckPlan,
    fill: Color,
    stroke: Color,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 56.dp,
) {
    DeckPlanOutline(
        plan = plan,
        modifier = modifier.size(width = width, height = height),
        fill = fill,
        stroke = stroke,
    )
}

/** Builds the outline path for [plan] scaled into [size]. */
internal fun deckPlanPath(plan: DeckPlan, size: Size): Path {
    val breadth = size.width * plan.breadthRatio.coerceIn(MIN_RATIO, 1f)
    val length = size.height * plan.lengthRatio.coerceIn(MIN_RATIO, 1f)
    val left = (size.width - breadth) / 2f
    val top = (size.height - length) / 2f
    val right = left + breadth
    val bottom = top + length

    return when (plan.shape) {
        PlanShape.RECTANGLE -> rectanglePath(left, top, right, bottom)

        PlanShape.L_SHAPE -> Path().apply {
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

        PlanShape.CUSTOM_POLYGON ->
            if (plan.polygon.size >= MIN_POLYGON_POINTS) {
                polygonPath(plan.polygon, size)
            } else {
                rectanglePath(left, top, right, bottom)
            }

        PlanShape.SHIP_HULL -> shipHullPath(
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

private fun rectanglePath(left: Float, top: Float, right: Float, bottom: Float): Path = Path().apply {
    moveTo(left, top)
    lineTo(right, top)
    lineTo(right, bottom)
    lineTo(left, bottom)
    close()
}

/**
 * A rectangle with a bow taper and a rounded stern.
 *
 * `bowSharpness` 0 leaves a blunt, full-breadth bow (a barge); 1 draws a fine point. The taper
 * runs over the forward [BOW_MIN_FRACTION]..[BOW_MAX_FRACTION] of the deck length, and the stern
 * corners are radiused by `sternRounding` x half-breadth. Always built bow-up, then mirrored
 * about the plan box's centre line when [bowAtTop] is false.
 */
private fun shipHullPath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    bowSharpness: Float,
    sternRounding: Float,
    bowAtTop: Boolean,
): Path {
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

    val path = Path().apply {
        moveTo(centreX - bowHalfWidth, top)
        quadraticTo(centreX, top - bowRun * BOW_CROWN, centreX + bowHalfWidth, top)
        quadraticTo(right, shoulderControlY, right, shoulderY)
        lineTo(right, sternShoulderY)
        if (sternRadius > 0f) {
            quadraticTo(right, bottom, right - sternRadius, bottom)
            lineTo(left + sternRadius, bottom)
            quadraticTo(left, bottom, left, sternShoulderY)
        } else {
            lineTo(right, bottom)
            lineTo(left, bottom)
        }
        lineTo(left, shoulderY)
        quadraticTo(left, shoulderControlY, centreX - bowHalfWidth, top)
        close()
    }

    if (!bowAtTop) {
        path.transform(
            Matrix().apply {
                translate(y = top + bottom)
                scale(y = -1f)
            },
        )
    }
    return path
}

private fun polygonPath(polygon: List<PlanPoint>, size: Size): Path = Path().apply {
    polygon.forEachIndexed { index, point ->
        val x = point.x.coerceIn(0f, 1f) * size.width
        val y = point.y.coerceIn(0f, 1f) * size.height
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val MIN_RATIO = 0.05f
private const val MIN_POLYGON_POINTS = 3
private const val L_NOTCH_FRACTION = 0.55f
private const val BOW_MIN_FRACTION = 0.10f
private const val BOW_MAX_FRACTION = 0.34f
private const val BOW_TIP_PINCH = 0.92f
private const val BOW_CROWN = 0.22f
private const val SHOULDER_PULL = 0.45f
