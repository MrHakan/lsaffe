package com.deckwatch.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.symbols.SYMBOL_VIEWPORT
import com.deckwatch.core.designsystem.symbols.SymbolLibrary
import com.deckwatch.core.designsystem.theme.ConditionColors
import com.deckwatch.core.designsystem.theme.DeckWatchTheme
import com.deckwatch.core.model.ConditionGrade
import java.util.concurrent.ConcurrentHashMap

/** Geometry of an equipment marker on the deck plan — §10.4. */
object MarkerDefaults {
    /** Default marker edge in a sheet or a list. */
    val Size: Dp = 32.dp

    /** Condition ring width as a fraction of the marker edge (2dp at 32dp). */
    const val RingRatio: Float = 0.0625f

    /** Fraction of the marker edge occupied by the pictogram. */
    const val PictogramRatio: Float = 0.72f

    /** Corner radius as a fraction of the marker edge. */
    const val CornerRatio: Float = 0.22f

    /** Diagonal hatch pitch for out-of-service equipment, as a fraction. */
    const val HatchPitchRatio: Float = 0.22f

    fun shapeFor(size: Dp): Shape = SymbolTileDefaults.shapeFor(size)
}

/**
 * The three pre-rasterised marker sizes the deck renderer switches between as
 * the user zooms — §7.2 performance budget.
 */
enum class MarkerLod(val size: Dp) {
    SMALL(16.dp),
    MEDIUM(24.dp),
    LARGE(32.dp),
    ;

    companion object {
        /** Picks the level of detail for a canvas [zoom] factor. */
        fun forZoom(zoom: Float): MarkerLod = when {
            zoom < 0.9f -> SMALL
            zoom < 1.8f -> MEDIUM
            else -> LARGE
        }
    }
}

/**
 * An equipment marker: symbol tile, condition ring, optional overdue badge and
 * tag label, out-of-service hatch and a selection pulse — §10.4.
 *
 * @param animate set to `false` to honour a reduced-motion preference; the
 *   selection ring is then drawn statically instead of pulsing.
 */
@Composable
fun EquipmentMarker(
    symbolKey: String,
    condition: ConditionGrade,
    modifier: Modifier = Modifier,
    size: Dp = MarkerDefaults.Size,
    mediaColor: Color? = null,
    overdueCount: Int = 0,
    tagLabel: String? = null,
    outOfService: Boolean = false,
    selected: Boolean = false,
    animate: Boolean = true,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val ground = mediaColor ?: SymbolLibrary.groundColor(symbolKey)
    val ringColor = ConditionColors.of(condition)
    val pictogram = SymbolLibrary.pictogramColorOn(ground)
    val shape = MarkerDefaults.shapeFor(size)
    val ringWidth = size * MarkerDefaults.RingRatio

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                PulseRing(size = size, color = ringColor, shape = shape, animate = animate)
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(ground)
                    .drawWithContent {
                        drawContent()
                        if (outOfService) {
                            drawOutOfServiceHatch(
                                color = pictogram,
                                strokePx = ringWidth.toPx(),
                                pitchPx = this.size.width * MarkerDefaults.HatchPitchRatio,
                            )
                        }
                    }
                    .border(ringWidth, ringColor, shape)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (contentDescription != null) {
                            Modifier.semantics { this.contentDescription = contentDescription }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SymbolLibrary.get(symbolKey),
                    contentDescription = null,
                    tint = pictogram,
                    modifier = Modifier.size(size * MarkerDefaults.PictogramRatio),
                )
            }
            if (overdueCount > 0) {
                OverdueBadge(
                    count = overdueCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = size * 0.22f, y = -size * 0.22f),
                )
            }
        }
        if (tagLabel != null) {
            Text(
                text = tagLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .widthIn(max = size * 2.6f),
            )
        }
    }
}

@Composable
private fun PulseRing(size: Dp, color: Color, shape: Shape, animate: Boolean) {
    val scale = if (animate) {
        val transition = rememberInfiniteTransition(label = "marker-pulse")
        val animated by transition.animateFloat(
            initialValue = 1.02f,
            targetValue = 1.34f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "marker-pulse-scale",
        )
        animated
    } else {
        1.28f
    }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = (2.3f - scale).coerceIn(0f, 1f)
            }
            .border(size * MarkerDefaults.RingRatio, color, shape),
    )
}

@Composable
private fun OverdueBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(ConditionColors.OutOfService),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else "$count",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private const val MAX_BADGE_COUNT = 9

// --------------------------------------------------------------- canvas path

/**
 * Draws one marker straight into a [DrawScope] — the path the deck renderer
 * uses, since it needs hundreds of markers per frame without composing any of
 * them (§7.2).
 *
 * @param sizePx the marker edge in pixels.
 * @param topLeft where the marker's bounding square starts.
 */
fun DrawScope.drawEquipmentMarker(
    symbol: ImageVector,
    ground: Color,
    ring: Color,
    sizePx: Float,
    topLeft: Offset,
    pictogram: Color = Color.White,
    ringWidthPx: Float = sizePx * MarkerDefaults.RingRatio,
    outOfService: Boolean = false,
    alpha: Float = 1f,
) {
    if (sizePx <= 0f) return
    val corner = CornerRadius(sizePx * MarkerDefaults.CornerRatio)
    val square = Size(sizePx, sizePx)

    drawRoundRect(
        color = ground,
        topLeft = topLeft,
        size = square,
        cornerRadius = corner,
        alpha = alpha,
    )

    val content = sizePx * MarkerDefaults.PictogramRatio
    val inset = (sizePx - content) / 2f
    val scale = content / SYMBOL_VIEWPORT
    withTransform({
        translate(topLeft.x + inset, topLeft.y + inset)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        for (path in symbol.pictogramPaths()) {
            drawPath(path = path, color = pictogram, alpha = alpha)
        }
    }

    if (outOfService) {
        val clip = Path().apply {
            addRoundRect(RoundRect(Rect(topLeft, square), corner))
        }
        clipPath(clip) {
            drawOutOfServiceHatch(
                color = pictogram,
                strokePx = ringWidthPx,
                pitchPx = sizePx * MarkerDefaults.HatchPitchRatio,
                origin = topLeft,
                extent = sizePx,
                alpha = alpha,
            )
        }
    }

    val half = ringWidthPx / 2f
    drawRoundRect(
        color = ring,
        topLeft = Offset(topLeft.x + half, topLeft.y + half),
        size = Size(sizePx - ringWidthPx, sizePx - ringWidthPx),
        cornerRadius = CornerRadius(corner.x - half, corner.y - half),
        style = Stroke(width = ringWidthPx),
        alpha = alpha,
    )
}

/**
 * Rasterises one marker at [lod] for the deck canvas's marker cache — §7.2.
 * Needs no composition, so the renderer can build its LOD atlas off the main
 * thread.
 */
fun renderMarkerBitmap(
    symbol: ImageVector,
    ground: Color,
    ring: Color,
    sizePx: Int,
    pictogram: Color = Color.White,
    outOfService: Boolean = false,
    density: Density = Density(1f),
): ImageBitmap {
    val edge = sizePx.coerceAtLeast(1)
    val bitmap = ImageBitmap(edge, edge)
    val canvas = Canvas(bitmap)
    val size = Size(edge.toFloat(), edge.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
        drawEquipmentMarker(
            symbol = symbol,
            ground = ground,
            ring = ring,
            sizePx = edge.toFloat(),
            topLeft = Offset.Zero,
            pictogram = pictogram,
            outOfService = outOfService,
        )
    }
    return bitmap
}

/** Rasterises the marker for [symbolKey] at one of the three canvas LODs. */
fun renderMarkerBitmap(
    symbolKey: String,
    condition: ConditionGrade,
    lod: MarkerLod,
    density: Density,
    mediaColor: Color? = null,
    outOfService: Boolean = false,
): ImageBitmap {
    val ground = mediaColor ?: SymbolLibrary.groundColor(symbolKey)
    return renderMarkerBitmap(
        symbol = SymbolLibrary.get(symbolKey),
        ground = ground,
        ring = ConditionColors.of(condition),
        sizePx = with(density) { lod.size.toPx() }.toInt(),
        pictogram = SymbolLibrary.pictogramColorOn(ground),
        outOfService = outOfService,
        density = density,
    )
}

private fun DrawScope.drawOutOfServiceHatch(
    color: Color,
    strokePx: Float,
    pitchPx: Float,
    origin: Offset = Offset.Zero,
    extent: Float = size.minDimension,
    alpha: Float = 1f,
) {
    if (pitchPx <= 0f) return
    var x = -extent
    while (x <= extent) {
        drawLine(
            color = color,
            start = Offset(origin.x + x, origin.y),
            end = Offset(origin.x + x + extent, origin.y + extent),
            strokeWidth = strokePx,
            alpha = alpha * HATCH_ALPHA,
        )
        x += pitchPx
    }
}

private const val HATCH_ALPHA = 0.85f

private val pictogramPathCache = ConcurrentHashMap<ImageVector, List<Path>>()

/**
 * The pictogram's sub-paths in viewport (24 x 24) coordinates, cached per
 * vector. The library's symbols use no group transforms, so a flat walk of the
 * tree is faithful.
 */
private fun ImageVector.pictogramPaths(): List<Path> =
    pictogramPathCache.getOrPut(this) {
        val paths = ArrayList<Path>()
        collectPaths(root, paths)
        paths
    }

private fun collectPaths(group: VectorGroup, into: MutableList<Path>) {
    for (node in group) {
        when (node) {
            is VectorPath -> {
                val path = PathParser().addPathNodes(node.pathData).toPath()
                path.fillType = node.pathFillType
                into += path
            }

            is VectorGroup -> collectPaths(node, into)
        }
    }
}

@Preview
@Composable
private fun EquipmentMarkerPreview() {
    DeckWatchTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EquipmentMarker(
                symbolKey = "FES001",
                condition = ConditionGrade.MONITOR,
                overdueCount = 2,
                tagLabel = "FE-UD-07",
                selected = true,
                animate = false,
            )
        }
    }
}
