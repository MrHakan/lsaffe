package com.deckwatch.feature.survivalcraft.schematic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Draws the elevation, letterboxed so the authored aspect ratio survives any screen width.
 *
 * The drawing itself is inert: hotspots are Compose widgets laid over it by the caller (see
 * [SchematicOverlay]), so every touch target is a real, focusable, TalkBack-labelled control
 * rather than a hit test inside a canvas.
 *
 * Leader lines are part of the art: where a hotspot's marker had to be moved out of a crowded
 * area, a hairline runs from the marker position back to the feature it names, with a small dot
 * on the feature itself — the way a technical illustration calls a part out.
 */
@Composable
internal fun SchematicDrawing(
    definition: SchematicDef,
    palette: SchematicPalette,
    modifier: Modifier = Modifier,
    baseStrokeWidth: Dp = 1.6.dp,
    hotspotTargetSize: Dp = HOTSPOT_TARGET,
) {
    Canvas(modifier = modifier) {
        val box = fitBox(size, definition.aspect)
        val base = baseStrokeWidth.toPx()
        val half = hotspotTargetSize.toPx() / 2f
        definition.shapes.forEach { shape -> drawShape(shape, box, palette, base) }
        definition.hotspots.filter { it.hasLeader }.forEach { hotspot ->
            val anchor = Offset(box.left + hotspot.x * box.width, box.top + hotspot.y * box.height)
            val marker = markerCentre(box, size, hotspot.touchX, hotspot.touchY, half)
            drawLine(
                color = palette.line,
                start = anchor,
                end = marker,
                strokeWidth = base * 0.6f,
            )
            drawCircle(color = palette.outline, radius = base * 1.1f, center = anchor)
        }
    }
}

/**
 * Where a hotspot's touch target is centred, clamped so a 48dp target authored near the edge of
 * the drawing still sits fully inside the canvas rather than being half cut off.
 */
internal fun markerCentre(box: Rect, canvas: Size, x: Float, y: Float, halfPx: Float): Offset {
    val maxX = (canvas.width - halfPx).coerceAtLeast(halfPx)
    val maxY = (canvas.height - halfPx).coerceAtLeast(halfPx)
    return Offset(
        x = (box.left + x * box.width).coerceIn(halfPx.coerceAtMost(maxX), maxX),
        y = (box.top + y * box.height).coerceIn(halfPx.coerceAtMost(maxY), maxY),
    )
}

/** The largest sub-rectangle of [available] with the given width/height ratio, centred. */
internal fun fitBox(available: Size, aspect: Float): Rect {
    if (available.width <= 0f || available.height <= 0f) return Rect(Offset.Zero, Size.Zero)
    val safeAspect = if (aspect <= 0f) 1f else aspect
    val width = min(available.width, available.height * safeAspect)
    val height = width / safeAspect
    val left = (available.width - width) / 2f
    val top = (available.height - height) / 2f
    return Rect(Offset(left, top), Size(width, height))
}

private fun DrawScope.drawShape(
    shape: SchematicShape,
    box: Rect,
    palette: SchematicPalette,
    base: Float,
) {
    val strokeColor = when (shape.stroke) {
        StrokeRole.OUTLINE -> palette.outline
        StrokeRole.STRUCTURE -> palette.structure
        StrokeRole.DETAIL -> palette.detail
        StrokeRole.LINE -> palette.line
        StrokeRole.NONE -> null
    }
    val fillColor = when (shape.fill) {
        FillRole.NONE -> null
        FillRole.BODY -> palette.body
        FillRole.PANEL -> palette.panel
        FillRole.ACCENT -> palette.accent
    }
    val stroke = Stroke(
        width = base * shape.weight,
        pathEffect = if (shape.dashed) {
            PathEffect.dashPathEffect(floatArrayOf(base * 3f, base * 2.5f))
        } else {
            null
        },
    )

    when (shape.kind) {
        ShapeKind.ELLIPSE -> {
            val points = shape.points
            if (points.size < 4) return
            val centre = box.point(points[0], points[1])
            val radius = Size(points[2] * box.width, points[3] * box.height)
            val topLeft = Offset(centre.x - radius.width, centre.y - radius.height)
            val diameter = Size(radius.width * 2f, radius.height * 2f)
            fillColor?.let { drawOval(color = it, topLeft = topLeft, size = diameter) }
            strokeColor?.let { drawOval(color = it, topLeft = topLeft, size = diameter, style = stroke) }
        }

        ShapeKind.RECT -> {
            val points = shape.points
            if (points.size < 4) return
            val topLeft = box.point(points[0], points[1])
            val bottomRight = box.point(points[2], points[3])
            val rectSize = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
            fillColor?.let { drawRect(color = it, topLeft = topLeft, size = rectSize) }
            strokeColor?.let { drawRect(color = it, topLeft = topLeft, size = rectSize, style = stroke) }
        }

        ShapeKind.POLYGON, ShapeKind.POLYLINE -> {
            val path = shape.toPath(box, close = shape.kind == ShapeKind.POLYGON) ?: return
            if (shape.kind == ShapeKind.POLYGON) {
                fillColor?.let { drawPath(path = path, color = it) }
            }
            strokeColor?.let { drawPath(path = path, color = it, style = stroke) }
        }
    }
}

private fun SchematicShape.toPath(box: Rect, close: Boolean): Path? {
    if (points.size < 4) return null
    val path = Path()
    val first = box.point(points[0], points[1])
    path.moveTo(first.x, first.y)
    var index = 2
    while (index + 1 < points.size) {
        val next = box.point(points[index], points[index + 1])
        path.lineTo(next.x, next.y)
        index += 2
    }
    if (close) path.close()
    return path
}

private fun Rect.point(x: Float, y: Float): Offset = Offset(left + x * width, top + y * height)

/**
 * Lays [content] over the drawing, handing it a mapper from 0..1 schematic coordinates to a
 * [Modifier] that positions a 48dp-plus touch target centred on that point.
 */
@Composable
internal fun SchematicOverlay(
    definition: SchematicDef,
    modifier: Modifier = Modifier,
    targetSize: Dp = HOTSPOT_TARGET,
    content: @Composable (place: (Float, Float) -> Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val canvas = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val box = fitBox(canvas, definition.aspect)
        val density = LocalDensity.current
        val half = with(density) { targetSize.toPx() / 2f }
        val place: (Float, Float) -> Modifier = { x, y ->
            val centre = markerCentre(box, canvas, x, y, half)
            Modifier
                .size(targetSize)
                .offset(
                    x = with(density) { (centre.x - half).toDp() },
                    y = with(density) { (centre.y - half).toDp() },
                )
        }
        Box(modifier = Modifier.fillMaxSize()) { content(place) }
    }
}

/** DESIGN_OVERHAUL rule 3 — every hotspot is at least a 48dp target. */
internal val HOTSPOT_TARGET: Dp = 48.dp
