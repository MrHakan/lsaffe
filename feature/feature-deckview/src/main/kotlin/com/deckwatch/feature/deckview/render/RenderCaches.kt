package com.deckwatch.feature.deckview.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.deckwatch.core.designsystem.components.MarkerLod
import com.deckwatch.core.designsystem.components.renderMarkerBitmap
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.feature.deckview.geometry.IsoProjection
import com.deckwatch.feature.deckview.geometry.Vec2
import kotlin.math.roundToInt

/** A bounded, access-ordered cache — plain LRU, no dependency. */
private class LruCache<K, V>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxEntries
    }

    fun getOrPut(key: K, create: () -> V): V = map[key] ?: create().also { map[key] = it }

    fun clear() = map.clear()

    private companion object {
        const val INITIAL_CAPACITY = 32
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * Projected outline paths, cached per §7.2: "precompute each deck's projected outline `Path` and
 * cache it, keyed by `(planHash, isoAngle, scale)`".
 *
 * The zoom is **not** part of the key. Zoom is applied as a canvas transform around the cached path,
 * so a pinch — which changes the scale sixty times a second — never invalidates the cache; the
 * `scale` in the key is the layout-derived plan size, which changes only when the viewport does. The
 * angle is quantised to [ANGLE_STEPS] steps per degree so that the flat/iso spring of §7.1B reuses
 * paths across its frames instead of allocating one per frame.
 */
class ProjectedPathCache(maxEntries: Int = MAX_ENTRIES) {

    private data class Key(val shapeHash: Int, val angleKey: Int, val scaleKey: Int)

    private val paths = LruCache<Key, Path>(maxEntries)

    /**
     * The closed path of [polygon] projected flat (rank 0) at [projection]'s angle and scale.
     *
     * @param shapeHash identifies the polygon — a deck's `planHash` (§7.2) or a zone's identity.
     * @param polygon evaluated only on a cache miss.
     */
    fun path(shapeHash: Int, projection: IsoProjection, polygon: () -> List<Vec2>): Path {
        val key = Key(
            shapeHash = shapeHash,
            angleKey = (projection.angle * ANGLE_STEPS).roundToInt(),
            scaleKey = projection.scale.roundToInt(),
        )
        return paths.getOrPut(key) { buildPath(polygon(), projection) }
    }

    fun clear() = paths.clear()

    private fun buildPath(polygon: List<Vec2>, projection: IsoProjection): Path {
        val path = Path()
        if (polygon.isEmpty()) return path
        polygon.forEachIndexed { index, point ->
            val projected = projection.project(point.x, point.y, levelZ = 0)
            if (index == 0) path.moveTo(projected.x, projected.y) else path.lineTo(projected.x, projected.y)
        }
        path.close()
        return path
    }

    private companion object {
        const val MAX_ENTRIES = 128
        const val ANGLE_STEPS = 4f
    }
}

/**
 * The three pre-rasterised marker LODs of §7.2, so that six hundred markers cost six hundred
 * `drawImage` calls and no vector work at all.
 */
class MarkerBitmapCache(private val density: Density, maxEntries: Int = MAX_ENTRIES) {

    private data class Key(
        val symbolKey: String,
        val condition: ConditionGrade,
        val outOfService: Boolean,
        val lod: MarkerLod,
    )

    private val bitmaps = LruCache<Key, ImageBitmap>(maxEntries)

    fun bitmap(
        symbolKey: String,
        condition: ConditionGrade,
        outOfService: Boolean,
        lod: MarkerLod,
    ): ImageBitmap = bitmaps.getOrPut(Key(symbolKey, condition, outOfService, lod)) {
        renderMarkerBitmap(
            symbolKey = symbolKey,
            condition = condition,
            lod = lod,
            density = density,
            outOfService = outOfService,
        )
    }

    /** The drawn edge of a marker at [lod], in pixels — markers keep a constant screen size. */
    fun sizePx(lod: MarkerLod): Float = with(density) { lod.size.toPx() }

    /** Where a marker bitmap's top-left goes for its centre to sit on [centre]. */
    fun topLeftFor(centre: Offset, lod: MarkerLod): Offset {
        val half = sizePx(lod) / 2f
        return Offset(centre.x - half, centre.y - half)
    }

    fun clear() = bitmaps.clear()

    private companion object {
        const val MAX_ENTRIES = 256
    }
}

/**
 * Measured tag labels (§10.4, drawn at zoom ≥ 1.5×).
 *
 * Text measurement is the one genuinely expensive thing left in the draw loop, so every label is
 * measured once and kept until the style changes.
 */
class LabelCache(maxEntries: Int = MAX_ENTRIES) {

    private var styleKey: TextStyle? = null
    private val labels = LruCache<String, TextLayoutResult>(maxEntries)

    fun measure(measurer: TextMeasurer, text: String, style: TextStyle): TextLayoutResult {
        if (styleKey != style) {
            labels.clear()
            styleKey = style
        }
        return labels.getOrPut(text) { measurer.measure(text = text, style = style, maxLines = 1) }
    }

    private companion object {
        const val MAX_ENTRIES = 192
    }
}
