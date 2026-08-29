package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drawing primitives for the DeckWatch symbol library — §10.2.
 *
 * **Provenance.** Every pictogram in this package is drawn from scratch out of
 * the geometric primitives below (rectangles, discs, annular bands, thick line
 * segments, teardrops). No vendor SVG, ISO/IMO artwork file or icon-library
 * path string was copied, traced or converted. See `docs/SYMBOL_LICENSING.md`.
 *
 * **House rules** — keep every symbol consistent:
 * * viewport is 24 x 24, content stays inside a ~2 unit margin (2f..22f);
 * * fills only, never strokes — a stroke would not scale with the tile;
 * * white fill on a transparent ground, so one vector serves every tint (§10.3);
 * * all sub-paths wind **clockwise** in screen coordinates (y grows downwards)
 *   so that overlapping shapes in a single `NonZero` path union instead of
 *   punching accidental holes. Deliberate holes go in a [cutout] path, whose
 *   even-odd fill rule turns any enclosed sub-path into a hole.
 */
internal const val SYMBOL_VIEWPORT = 24f

private val SymbolWhite = SolidColor(Color.White)

private const val DEG = (Math.PI / 180.0).toFloat()

/** Builds one 24x24 white-on-transparent pictogram. */
internal fun symbolVector(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = SYMBOL_VIEWPORT,
        viewportHeight = SYMBOL_VIEWPORT,
    ).apply(content).build()

/** A solid white sub-path group; overlapping clockwise shapes union. */
internal fun ImageVector.Builder.solid(pathBuilder: PathBuilder.() -> Unit): ImageVector.Builder =
    path(
        fill = SymbolWhite,
        stroke = null,
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathBuilder,
    )

/** A white path whose enclosed sub-paths become holes (rings, cut details). */
internal fun ImageVector.Builder.cutout(pathBuilder: PathBuilder.() -> Unit): ImageVector.Builder =
    path(
        fill = SymbolWhite,
        stroke = null,
        pathFillType = PathFillType.EvenOdd,
        pathBuilder = pathBuilder,
    )

// ---------------------------------------------------------------- primitives

/** Axis-aligned rectangle, clockwise. */
internal fun PathBuilder.rect(left: Float, top: Float, right: Float, bottom: Float) {
    moveTo(left, top)
    lineTo(right, top)
    lineTo(right, bottom)
    lineTo(left, bottom)
    close()
}

/** Rounded rectangle, clockwise. */
internal fun PathBuilder.roundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    val r = minOf(radius, (right - left) / 2f, (bottom - top) / 2f)
    if (r <= 0f) {
        rect(left, top, right, bottom)
        return
    }
    moveTo(left + r, top)
    lineTo(right - r, top)
    arcTo(r, r, 0f, false, true, right, top + r)
    lineTo(right, bottom - r)
    arcTo(r, r, 0f, false, true, right - r, bottom)
    lineTo(left + r, bottom)
    arcTo(r, r, 0f, false, true, left, bottom - r)
    lineTo(left, top + r)
    arcTo(r, r, 0f, false, true, left + r, top)
    close()
}

/** Full disc, clockwise. Inside a [cutout] path it becomes a hole. */
internal fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx - radius, cy)
    arcTo(radius, radius, 0f, true, true, cx + radius, cy)
    arcTo(radius, radius, 0f, true, true, cx - radius, cy)
    close()
}

/** Half disc sitting on the horizontal line [baseY], bulging upwards. */
internal fun PathBuilder.domeUp(cx: Float, baseY: Float, radius: Float) {
    moveTo(cx - radius, baseY)
    arcTo(radius, radius, 0f, false, true, cx + radius, baseY)
    close()
}

/** Half disc hanging under the horizontal line [baseY], bulging downwards. */
internal fun PathBuilder.domeDown(cx: Float, baseY: Float, radius: Float) {
    moveTo(cx + radius, baseY)
    arcTo(radius, radius, 0f, false, true, cx - radius, baseY)
    close()
}

/**
 * A thick line segment — the library's substitute for a stroke. Endpoints are
 * normalised so the quad always winds clockwise.
 */
internal fun PathBuilder.bar(x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
    val swap = x2 < x1 || (x2 == x1 && y2 < y1)
    val ax = if (swap) x2 else x1
    val ay = if (swap) y2 else y1
    val bx = if (swap) x1 else x2
    val by = if (swap) y1 else y2
    val dx = bx - ax
    val dy = by - ay
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1e-4f) return
    val nx = -dy / len * (width / 2f)
    val ny = dx / len * (width / 2f)
    moveTo(ax - nx, ay - ny)
    lineTo(bx - nx, by - ny)
    lineTo(bx + nx, by + ny)
    lineTo(ax + nx, ay + ny)
    close()
}

/**
 * An annular band of [width] centred on [radius], from [startDeg] clockwise
 * through [sweepDeg]. 0° points right (+x), 90° points down (+y).
 */
internal fun PathBuilder.arcBand(
    cx: Float,
    cy: Float,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
    width: Float,
) {
    val start = if (sweepDeg < 0f) startDeg + sweepDeg else startDeg
    val sweep = abs(sweepDeg)
    if (sweep < 0.01f) return
    val ro = radius + width / 2f
    val ri = (radius - width / 2f).coerceAtLeast(0f)
    val a0 = start * DEG
    val a1 = (start + sweep) * DEG
    val big = sweep > 180f
    moveTo(cx + ro * cos(a0), cy + ro * sin(a0))
    arcTo(ro, ro, 0f, big, true, cx + ro * cos(a1), cy + ro * sin(a1))
    lineTo(cx + ri * cos(a1), cy + ri * sin(a1))
    arcTo(ri, ri, 0f, big, false, cx + ri * cos(a0), cy + ri * sin(a0))
    close()
}

/**
 * Radial ticks / light rays: one bar per angle, running from radius [from] to
 * radius [to] around ([cx], [cy]). 0° points right, 90° points down.
 */
internal fun PathBuilder.rays(
    cx: Float,
    cy: Float,
    from: Float,
    to: Float,
    width: Float,
    vararg degrees: Float,
) {
    for (d in degrees) {
        val a = d * DEG
        bar(cx + from * cos(a), cy + from * sin(a), cx + to * cos(a), cy + to * sin(a), width)
    }
}

/** Triangle through three points; give them in clockwise order. */
internal fun PathBuilder.triangle(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    x3: Float,
    y3: Float,
) {
    moveTo(x1, y1)
    lineTo(x2, y2)
    lineTo(x3, y3)
    close()
}

/** Polygon from flat clockwise `x, y, x, y…` coordinates. */
internal fun PathBuilder.polygon(vararg xy: Float) {
    if (xy.size < 6) return
    moveTo(xy[0], xy[1])
    var i = 2
    while (i < xy.size - 1) {
        lineTo(xy[i], xy[i + 1])
        i += 2
    }
    close()
}

/** Point of the compass in the marker sense: a flat teardrop / flame. */
internal fun PathBuilder.flame(cx: Float, bottomY: Float, height: Float, halfWidth: Float) {
    val h = height
    val w = halfWidth
    moveTo(cx, bottomY - h)
    curveTo(
        cx + w * 0.30f, bottomY - h * 0.74f,
        cx + w, bottomY - h * 0.62f,
        cx + w, bottomY - h * 0.32f,
    )
    curveTo(
        cx + w, bottomY - h * 0.05f,
        cx + w * 0.58f, bottomY,
        cx, bottomY,
    )
    curveTo(
        cx - w * 0.58f, bottomY,
        cx - w, bottomY - h * 0.05f,
        cx - w, bottomY - h * 0.32f,
    )
    curveTo(
        cx - w, bottomY - h * 0.68f,
        cx - w * 0.16f, bottomY - h * 0.50f,
        cx, bottomY - h,
    )
    close()
}

/** A liquid drop: round bottom, pointed top. */
internal fun PathBuilder.drop(cx: Float, topY: Float, height: Float, halfWidth: Float) {
    val bottom = topY + height
    val r = halfWidth
    moveTo(cx, topY)
    curveTo(
        cx + r * 0.45f, topY + height * 0.32f,
        cx + r, topY + height * 0.48f,
        cx + r, bottom - r,
    )
    arcTo(r, r, 0f, true, true, cx - r, bottom - r)
    curveTo(
        cx - r, topY + height * 0.48f,
        cx - r * 0.45f, topY + height * 0.32f,
        cx, topY,
    )
    close()
}

/** Lens / eye outline. Put it in a [cutout] with a pupil circle for the iris. */
internal fun PathBuilder.lens(cx: Float, cy: Float, halfWidth: Float, halfHeight: Float) {
    moveTo(cx - halfWidth, cy)
    curveTo(
        cx - halfWidth * 0.45f, cy - halfHeight * 2.1f,
        cx + halfWidth * 0.45f, cy - halfHeight * 2.1f,
        cx + halfWidth, cy,
    )
    curveTo(
        cx + halfWidth * 0.45f, cy + halfHeight * 2.1f,
        cx - halfWidth * 0.45f, cy + halfHeight * 2.1f,
        cx - halfWidth, cy,
    )
    close()
}

/** Head-and-shoulders figure: disc head over a rounded torso. */
internal fun PathBuilder.person(cx: Float, headCy: Float, scale: Float) {
    val headR = 2.2f * scale
    circle(cx, headCy, headR)
    val top = headCy + headR * 1.35f
    val height = 7.4f * scale
    val halfW = 3.4f * scale
    moveTo(cx - halfW * 0.55f, top)
    lineTo(cx + halfW * 0.55f, top)
    curveTo(
        cx + halfW, top + height * 0.20f,
        cx + halfW, top + height * 0.45f,
        cx + halfW, top + height,
    )
    lineTo(cx - halfW, top + height)
    curveTo(
        cx - halfW, top + height * 0.45f,
        cx - halfW, top + height * 0.20f,
        cx - halfW * 0.55f, top,
    )
    close()
}

/** Lightning bolt in the box ([left], [top], [width], [height]), clockwise. */
internal fun PathBuilder.lightning(left: Float, top: Float, width: Float, height: Float) {
    polygon(
        left + width * 0.40f, top,
        left + width, top,
        left + width * 0.53f, top + height * 0.46f,
        left + width * 0.90f, top + height * 0.46f,
        left, top + height,
        left + width * 0.33f, top + height * 0.51f,
        left, top + height * 0.51f,
    )
}

/**
 * Cross / plus sign as ONE clockwise 12-gon (never two overlapping bars) so
 * that it also works as a hole inside a [cutout] path.
 */
internal fun PathBuilder.cross(cx: Float, cy: Float, arm: Float, thickness: Float) {
    val t = thickness / 2f
    polygon(
        cx - t, cy - arm,
        cx + t, cy - arm,
        cx + t, cy - t,
        cx + arm, cy - t,
        cx + arm, cy + t,
        cx + t, cy + t,
        cx + t, cy + arm,
        cx - t, cy + arm,
        cx - t, cy + t,
        cx - arm, cy + t,
        cx - arm, cy - t,
        cx - t, cy - t,
    )
}

/** Heart outline, clockwise. Sits in the box centred on ([cx], mid of y-span). */
internal fun PathBuilder.heart(cx: Float, top: Float, bottom: Float, halfWidth: Float) {
    val h = bottom - top
    val w = halfWidth
    moveTo(cx, bottom)
    curveTo(
        cx - w * 0.95f, bottom - h * 0.55f,
        cx - w, top + h * 0.30f,
        cx - w * 0.50f, top,
    )
    curveTo(
        cx - w * 0.18f, top - h * 0.05f,
        cx, top + h * 0.10f,
        cx, top + h * 0.22f,
    )
    curveTo(
        cx, top + h * 0.10f,
        cx + w * 0.18f, top - h * 0.05f,
        cx + w * 0.50f, top,
    )
    curveTo(
        cx + w, top + h * 0.30f,
        cx + w * 0.95f, bottom - h * 0.55f,
        cx, bottom,
    )
    close()
}
