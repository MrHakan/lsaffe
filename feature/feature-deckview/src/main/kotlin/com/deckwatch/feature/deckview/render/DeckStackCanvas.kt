package com.deckwatch.feature.deckview.render

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.components.MarkerLod
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.feature.deckview.model.ConditionAggregate
import com.deckwatch.feature.deckview.model.DeckNode
import com.deckwatch.feature.deckview.model.MarkerNode
import com.deckwatch.feature.deckview.model.StackRenderModel
import com.deckwatch.feature.deckview.model.ZoneNode
import kotlin.math.min

/** Screen-space constants of the 2.5D renderer — §7.2. */
object DeckRenderDefaults {
    /** §7.2: the deck separation is a constant screen value, 64dp at zoom 1. */
    val DeckHeight: Dp = 64.dp

    /** §7.2: "a thin extruded edge (6–10dp) on the near sides to give it physical thickness". */
    val Extrusion: Dp = 8.dp

    val OutlineWidth: Dp = 1.5.dp
    val GridWidth: Dp = 0.75.dp

    /** How much of the viewport one deck spans at zoom 1, in stack mode and in deck mode. */
    const val STACK_PLAN_FRACTION: Float = 0.52f
    const val DECK_PLAN_FRACTION: Float = 0.78f

    /** Below this zoom individual markers are replaced by one aggregated dot per zone (§7.2). */
    const val AGGREGATE_ZOOM: Float = 0.7f

    /** Tag labels appear at zoom ≥ 1.5× (§10.4). */
    const val LABEL_ZOOM: Float = 1.5f

    /** Touch radius for picking a marker, generous for a gloved thumb (C6). */
    val MarkerHitRadius: Dp = 26.dp

    /** How far outside the viewport a marker is still drawn, so panning does not pop. */
    val CullMargin: Dp = 48.dp

    val ZoneDotRadius: Dp = 7.dp
    val OverdueDotRadius: Dp = 4.dp
    val ShakeAmplitude: Dp = 10.dp
}

/** Published by the canvas each frame so the gesture layer hit-tests the pixels actually drawn. */
class DeckLayoutHolder {
    /** Not snapshot state on purpose: writing it during draw must not invalidate anything. */
    @Volatile
    var layout: StackLayout? = null
}

/**
 * The 2.5D deck stack — §7.2.
 *
 * One `Canvas`, painter's algorithm from the bottom deck up. Per deck: the extruded near edge, the
 * plan fill, the zone polygons, the optional grid, the outline, then the markers, their labels and
 * the selection overlay. Everything that changes while a finger is down is read from
 * [transform] *inside* the draw lambda, so a pan or a pinch invalidates the draw phase only.
 *
 * @param layoutHolder receives the layout drawn each frame, so the gesture layer resolves taps
 *   against exactly the pixels on screen.
 */
@Composable
@Suppress("LongParameterList") // A renderer: every argument is a distinct visual input.
fun DeckStackCanvas(
    model: StackRenderModel,
    transform: DeckTransformState,
    layoutHolder: DeckLayoutHolder,
    modifier: Modifier = Modifier,
    deckMode: Boolean = false,
    activeDeckId: String? = null,
    focusedDeckId: String? = null,
    selectedEquipmentId: String? = null,
    showGrid: Boolean = false,
    reduceMotion: Boolean = false,
) {
    val density = LocalDensity.current
    val pathCache = remember(density) { ProjectedPathCache() }
    val markerCache = remember(density) { MarkerBitmapCache(density) }
    val labelCache = remember { LabelCache() }
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface)

    val palette = remember(scheme) {
        DeckPalette(
            surface = scheme.surfaceContainerHighest,
            edge = scheme.outlineVariant,
            outline = scheme.outline,
            grid = scheme.outlineVariant.copy(alpha = GRID_ALPHA),
            focusRing = scheme.primary,
            labelShade = scheme.surface.copy(alpha = LABEL_SHADE_ALPHA),
        )
    }
    val pulse = rememberPulse(reduceMotion)

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.minDimension <= 0f) return@Canvas
        val planFraction = if (deckMode) {
            DeckRenderDefaults.DECK_PLAN_FRACTION
        } else {
            DeckRenderDefaults.STACK_PLAN_FRACTION
        }
        val decks = DeckVisibility.visibleDecks(
            model = model,
            deckMode = deckMode,
            activeDeckId = activeDeckId,
            isolatedDeckId = transform.isolatedDeckId,
        )
        val layout = StackLayout.of(
            viewport = size,
            planSizePx = min(size.width, size.height) * planFraction,
            // Deck mode shows one deck, so the stack's vertical offset collapses: a deck with rank
            // 7 must sit in the middle of the screen, not seven deck heights above it.
            deckHeightPx = if (deckMode) 0f else DeckRenderDefaults.DeckHeight.toPx(),
            deckCount = if (deckMode) 1 else model.decks.size,
            angleDeg = transform.angleDeg,
            zoom = transform.zoom,
            spread = transform.spread,
            pan = transform.pan + Offset(transform.shakePx, 0f),
        )
        layoutHolder.layout = layout

        val frame = FrameContext(
            layout = layout,
            palette = palette,
            pathCache = pathCache,
            markerCache = markerCache,
            labelCache = labelCache,
            measurer = measurer,
            labelStyle = labelStyle,
            lod = MarkerLod.forZoom(layout.zoom),
            aggregated = layout.zoom < DeckRenderDefaults.AGGREGATE_ZOOM,
            showLabels = layout.zoom >= DeckRenderDefaults.LABEL_ZOOM,
            showGrid = showGrid,
            selectedEquipmentId = selectedEquipmentId,
            pulse = pulse,
            dragged = transform.drag,
        )

        for (deck in decks) {
            val alpha = if (deckMode || transform.isolatedDeckId != null) {
                1f
            } else {
                DeckVisibility.alphaFor(deck, focusedDeckId, model)
            }
            if (alpha <= 0f) continue
            drawDeckSurface(deck, frame, alpha, focused = deck.deckId == focusedDeckId)
            drawDeckMarkers(deck, frame, alpha)
        }
        frame.dragged?.let { drag -> drawDraggedMarker(drag, model, frame) }
    }
}

// --------------------------------------------------------------------- frame plumbing

private data class DeckPalette(
    val surface: Color,
    val edge: Color,
    val outline: Color,
    val grid: Color,
    val focusRing: Color,
    val labelShade: Color,
)

@Suppress("LongParameterList") // Value bundle for the draw helpers; built once per frame.
private class FrameContext(
    val layout: StackLayout,
    val palette: DeckPalette,
    val pathCache: ProjectedPathCache,
    val markerCache: MarkerBitmapCache,
    val labelCache: LabelCache,
    val measurer: TextMeasurer,
    val labelStyle: TextStyle,
    val lod: MarkerLod,
    val aggregated: Boolean,
    val showLabels: Boolean,
    val showGrid: Boolean,
    val selectedEquipmentId: String?,
    /** Read inside the draw lambda, never in composition — the pulse must not recompose anything. */
    val pulse: State<Float>,
    val dragged: MarkerDrag?,
)

@Composable
private fun rememberPulse(reduceMotion: Boolean): State<Float> {
    if (reduceMotion) return remember { mutableStateOf(STATIC_PULSE) }
    val transition = rememberInfiniteTransition(label = "deck-selection-pulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = MAX_PULSE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "deck-selection-pulse-scale",
    )
}

// --------------------------------------------------------------------- deck surface

/** Extruded near edge → fill → zones → grid → outline (§7.2 draw order). */
private fun DrawScope.drawDeckSurface(
    deck: DeckNode,
    frame: FrameContext,
    alpha: Float,
    focused: Boolean,
) {
    val layout = frame.layout
    val origin = layout.deckOrigin(deck.levelZ)
    val zoom = layout.zoom
    val outlinePath = frame.pathCache.path(deck.planHash, layout.basePathProjection) { deck.outline }
    val fill = deck.colorTint?.let { Color(it) } ?: frame.palette.surface
    val extrusion = DeckRenderDefaults.Extrusion.toPx() / zoom

    withTransform({
        translate(origin.x, origin.y)
        scale(zoom, zoom, pivot = Offset.Zero)
    }) {
        translate(0f, extrusion) {
            drawPath(outlinePath, frame.palette.edge, alpha = alpha * EDGE_ALPHA)
        }
        drawPath(outlinePath, fill, alpha = alpha * FILL_ALPHA)

        for (zone in deck.zones) {
            drawZone(zone, frame, alpha)
        }
        if (frame.showGrid && focused) {
            drawPlanGrid(frame, alpha)
        }
        drawPath(
            path = outlinePath,
            color = if (focused) frame.palette.focusRing else frame.palette.outline,
            alpha = alpha,
            style = Stroke(width = DeckRenderDefaults.OutlineWidth.toPx() / zoom * if (focused) 2f else 1f),
        )
    }
}

private fun DrawScope.drawZone(zone: ZoneNode, frame: FrameContext, alpha: Float) {
    if (zone.polygon.size < MIN_POLYGON) return
    val path = frame.pathCache.path(zone.polygon.hashCode(), frame.layout.basePathProjection) {
        zone.polygon
    }
    drawPath(path, Color(zone.colorArgb), alpha = alpha * ZONE_ALPHA)
}

/** The optional placement grid of §7.2, projected with the deck so it lies flat on it. */
private fun DrawScope.drawPlanGrid(frame: FrameContext, alpha: Float) {
    val projection = frame.layout.basePathProjection
    val width = DeckRenderDefaults.GridWidth.toPx() / frame.layout.zoom
    for (step in 0..GRID_DIVISIONS) {
        val t = step.toFloat() / GRID_DIVISIONS
        val top = projection.project(t, 0f)
        val bottom = projection.project(t, 1f)
        val left = projection.project(0f, t)
        val right = projection.project(1f, t)
        drawLine(frame.palette.grid, Offset(top.x, top.y), Offset(bottom.x, bottom.y), width, alpha = alpha)
        drawLine(frame.palette.grid, Offset(left.x, left.y), Offset(right.x, right.y), width, alpha = alpha)
    }
}

// --------------------------------------------------------------------- markers

private fun DrawScope.drawDeckMarkers(deck: DeckNode, frame: FrameContext, alpha: Float) {
    if (frame.aggregated) {
        drawAggregatedDots(deck, frame, alpha)
        return
    }
    val layout = frame.layout
    val margin = DeckRenderDefaults.CullMargin.toPx()
    val markerSize = frame.markerCache.sizePx(frame.lod)
    for (marker in deck.markers) {
        if (marker.equipmentId == frame.dragged?.equipmentId) continue
        val centre = layout.toScreen(deck.levelZ, marker.position)
        // Cull markers outside the viewport before drawing anything — §7.2.
        if (isOffscreen(centre, margin)) continue
        drawMarker(marker, centre, markerSize, frame, alpha)
    }
}

/** True when a marker's centre is far enough outside the viewport not to be worth drawing. */
private fun DrawScope.isOffscreen(centre: Offset, margin: Float): Boolean {
    val horizontal = centre.x < -margin || centre.x > size.width + margin
    val vertical = centre.y < -margin || centre.y > size.height + margin
    return horizontal || vertical
}

private fun DrawScope.drawMarker(
    marker: MarkerNode,
    centre: Offset,
    markerSize: Float,
    frame: FrameContext,
    alpha: Float,
) {
    val selected = marker.equipmentId == frame.selectedEquipmentId
    if (selected) {
        val pulse = frame.pulse.value
        drawCircle(
            color = ConditionColors.of(marker.condition),
            radius = markerSize * SELECTION_RADIUS_RATIO * pulse,
            center = centre,
            alpha = alpha * (PULSE_FADE_BASE - pulse).coerceIn(0f, 1f),
            style = Stroke(width = markerSize * SELECTION_STROKE_RATIO),
        )
    }
    val bitmap = frame.markerCache.bitmap(
        symbolKey = marker.symbolKey,
        condition = marker.condition,
        outOfService = marker.outOfService,
        lod = frame.lod,
    )
    drawImage(
        image = bitmap,
        topLeft = frame.markerCache.topLeftFor(centre, frame.lod),
        alpha = alpha,
    )
    if (marker.overdue) {
        drawCircle(
            color = ConditionColors.OutOfService,
            radius = DeckRenderDefaults.OverdueDotRadius.toPx(),
            center = Offset(centre.x + markerSize / 2f, centre.y - markerSize / 2f),
            alpha = alpha,
        )
    }
    if (frame.showLabels) {
        drawTagLabel(marker.tag, centre, markerSize, frame, alpha)
    }
}

private fun DrawScope.drawTagLabel(
    tag: String,
    centre: Offset,
    markerSize: Float,
    frame: FrameContext,
    alpha: Float,
) {
    val measured = frame.labelCache.measure(frame.measurer, tag, frame.labelStyle)
    val topLeft = Offset(
        x = centre.x - measured.size.width / 2f,
        y = centre.y + markerSize / 2f + LABEL_GAP_PX,
    )
    drawRect(
        color = frame.palette.labelShade,
        topLeft = Offset(topLeft.x - LABEL_PAD_PX, topLeft.y),
        size = Size(
            width = measured.size.width + LABEL_PAD_PX * 2f,
            height = measured.size.height.toFloat(),
        ),
        alpha = alpha,
    )
    drawText(textLayoutResult = measured, topLeft = topLeft, alpha = alpha)
}

/**
 * The low-zoom LOD of §7.2: one dot per zone, coloured by the worst condition in it, plus one dot
 * for the deck's unzoned equipment.
 */
private fun DrawScope.drawAggregatedDots(deck: DeckNode, frame: FrameContext, alpha: Float) {
    val radius = DeckRenderDefaults.ZoneDotRadius.toPx()
    for (zone in deck.zones) {
        if (zone.markerCount == 0) continue
        val centre = frame.layout.toScreen(deck.levelZ, zone.centroid)
        drawCircle(ConditionColors.of(zone.worstCondition), radius, centre, alpha = alpha)
        if (zone.overdueCount > 0) {
            drawCircle(
                color = ConditionColors.OutOfService,
                radius = DeckRenderDefaults.OverdueDotRadius.toPx(),
                center = Offset(centre.x + radius, centre.y - radius),
                alpha = alpha,
            )
        }
    }
    val unzoned = deck.unzonedMarkers
    if (unzoned.isNotEmpty()) {
        val centre = frame.layout.toScreen(deck.levelZ, PLAN_CENTRE, PLAN_CENTRE)
        drawCircle(
            color = ConditionColors.of(ConditionAggregate.worstOf(unzoned)),
            radius = radius,
            center = centre,
            alpha = alpha,
        )
    }
}

/** The picked-up marker, drawn last so it floats over everything, with its live coordinates (§7.2). */
private fun DrawScope.drawDraggedMarker(drag: MarkerDrag, model: StackRenderModel, frame: FrameContext) {
    val deck = model.deck(drag.deckId) ?: return
    val marker = deck.markers.firstOrNull { it.equipmentId == drag.equipmentId } ?: return
    val centre = frame.layout.toScreen(drag.levelZ, drag.planX, drag.planY)
    val bitmap = frame.markerCache.bitmap(
        symbolKey = marker.symbolKey,
        condition = marker.condition,
        outOfService = marker.outOfService,
        lod = MarkerLod.LARGE,
    )
    val size = frame.markerCache.sizePx(MarkerLod.LARGE)
    drawCircle(
        color = if (drag.insideOutline) frame.palette.focusRing else ConditionColors.OutOfService,
        radius = size * DRAG_HALO_RATIO,
        center = centre,
        alpha = DRAG_HALO_ALPHA,
    )
    drawImage(image = bitmap, topLeft = Offset(centre.x - size / 2f, centre.y - size / 2f))
    val readout = frame.measurer.measure(
        text = formatCoordinates(drag.planX, drag.planY),
        style = frame.labelStyle,
        maxLines = 1,
    )
    drawText(
        textLayoutResult = readout,
        topLeft = Offset(centre.x - readout.size.width / 2f, centre.y - size - readout.size.height),
    )
}

private fun formatCoordinates(x: Float, y: Float): String {
    val px = (x * COORDINATE_SCALE).toInt()
    val py = (y * COORDINATE_SCALE).toInt()
    return "$px, $py"
}

private const val MIN_POLYGON = 3
private const val GRID_DIVISIONS = 20
private const val PLAN_CENTRE = 0.5f
private const val EDGE_ALPHA = 0.9f
private const val FILL_ALPHA = 0.96f
private const val ZONE_ALPHA = 0.55f
private const val GRID_ALPHA = 0.5f
private const val LABEL_SHADE_ALPHA = 0.72f
private const val LABEL_GAP_PX = 3f
private const val LABEL_PAD_PX = 2f
private const val SELECTION_RADIUS_RATIO = 0.78f
private const val SELECTION_STROKE_RATIO = 0.09f
private const val PULSE_MILLIS = 900
private const val MAX_PULSE = 1.34f

/** Fades the pulse ring out as it grows: alpha = base − scale, matching `EquipmentMarker`. */
private const val PULSE_FADE_BASE = 2.3f
private const val STATIC_PULSE = 1.2f
private const val DRAG_HALO_RATIO = 0.85f
private const val DRAG_HALO_ALPHA = 0.35f

/** The live readout shows plan percent, which is what the officer can reason about. */
private const val COORDINATE_SCALE = 100f
