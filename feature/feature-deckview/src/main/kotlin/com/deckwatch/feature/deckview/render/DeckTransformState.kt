package com.deckwatch.feature.deckview.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.deckwatch.feature.deckview.geometry.IsoProjection
import com.deckwatch.feature.deckview.geometry.ZoomLimits

/** A marker the officer has picked up and is dragging — §7.2. */
data class MarkerDrag(
    val equipmentId: String,
    val deckId: String,
    val levelZ: Int,
    /** Live plan coordinates, already snapped when the grid is on. */
    val planX: Float,
    val planY: Float,
    /** False once the finger leaves the deck outline — the drop will be refused. */
    val insideOutline: Boolean,
)

/**
 * Everything about the camera that changes while a finger is down.
 *
 * This is deliberately **not** in the view model and **not** read during composition: the canvas
 * reads it inside its draw lambda, so a pan, a pinch or a fan of the stack invalidates the draw
 * phase only and never recomposes (§7.2 performance budget). Chrome that genuinely needs to show a
 * value — the spread slider — reads it inside its own small composable so the recomposition stays
 * there.
 */
@Stable
class DeckTransformState(
    initialZoom: Float = 1f,
    initialSpread: Float = IsoProjection.DEFAULT_SPREAD,
    initialPan: Offset = Offset.Zero,
    initialYaw: Float = IsoProjection.NO_YAW_DEG,
) {
    var pan: Offset by mutableStateOf(initialPan)
    var zoom: Float by mutableFloatStateOf(initialZoom)
    var spread: Float by mutableFloatStateOf(initialSpread)

    /** The animated isometric angle — the single float behind §7.1B's flat/iso toggle. */
    var angleDeg: Float by mutableFloatStateOf(IsoProjection.DEFAULT_ANGLE_DEG)

    /**
     * How far the vessel is turned about its own centre, driven by the compass strip.
     *
     * It lives here rather than in the view model because it changes with every frame of a drag,
     * exactly like [pan] and [zoom], and for the same reason must not recompose anything.
     */
    var yawDeg: Float by mutableFloatStateOf(IsoProjection.normaliseYaw(initialYaw))

    /** Set while a deck is long-pressed: everything else is hidden (§7.2 occlusion and focus). */
    var isolatedDeckId: String? by mutableStateOf(null)

    /** The marker currently being repositioned, if any. */
    var drag: MarkerDrag? by mutableStateOf(null)

    /** Horizontal shake applied to a refused drop (§7.2). */
    var shakePx: Float by mutableFloatStateOf(0f)

    fun panBy(delta: Offset) {
        pan += delta
    }

    fun zoomBy(factor: Float, deckMode: Boolean) {
        zoom = ZoomLimits.clamp(zoom * factor, deckMode)
    }

    fun updateZoom(value: Float, deckMode: Boolean) {
        zoom = ZoomLimits.clamp(value, deckMode)
    }

    fun spreadBy(factor: Float) {
        spread = IsoProjection.clampSpread(spread * factor)
    }

    fun updateSpread(value: Float) {
        spread = IsoProjection.clampSpread(value)
    }

    /** Turns the vessel by [deltaDeg], clockwise on screen for a positive value. */
    fun yawBy(deltaDeg: Float) {
        yawDeg = IsoProjection.normaliseYaw(yawDeg + deltaDeg)
    }

    /**
     * Puts the bow back at the top.
     *
     * Deliberately not part of [reset]: zoom-to-fit is about where the camera is, and an officer
     * who has turned the ship to see the port side does not expect a double-tap on the deck to
     * spin it back. The compass has its own way to level it.
     */
    fun levelYaw() {
        yawDeg = IsoProjection.NO_YAW_DEG
    }

    fun reset() {
        pan = Offset.Zero
        zoom = 1f
    }

    /**
     * Flies the camera to a deck — the spine's tap target and the double-tap zoom-to-fit of §7.2.
     *
     * @param targetPan where the pan must end up for the deck to sit in the middle of the viewport.
     * @param instant true when the system animator scale is 0 (reduced motion — §14).
     */
    suspend fun flyTo(targetPan: Offset, targetZoom: Float, deckMode: Boolean, instant: Boolean) {
        val clampedZoom = ZoomLimits.clamp(targetZoom, deckMode)
        if (instant) {
            pan = targetPan
            zoom = clampedZoom
            return
        }
        val startPan = pan
        val startZoom = zoom
        val animatable = Animatable(0f)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) {
            pan = Offset(
                x = startPan.x + (targetPan.x - startPan.x) * value,
                y = startPan.y + (targetPan.y - startPan.y) * value,
            )
            zoom = startZoom + (clampedZoom - startZoom) * value
        }
    }

    /** The refused-drop shake of §7.2: three quick lateral flicks, or nothing under reduced motion. */
    suspend fun shake(amplitudePx: Float, instant: Boolean) {
        if (instant) return
        val animatable = Animatable(0f, Float.VectorConverter)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SHAKE_MILLIS),
        ) {
            val phase = value * SHAKE_CYCLES * TWO_PI
            shakePx = amplitudePx * (1f - value) * kotlin.math.sin(phase)
        }
        shakePx = 0f
    }

    companion object {
        private const val SHAKE_MILLIS = 320
        private const val SHAKE_CYCLES = 3f
        private const val TWO_PI = (2.0 * Math.PI).toFloat()

        val Saver: Saver<DeckTransformState, Any> = listSaver<DeckTransformState, Float>(
            save = { listOf(it.zoom, it.spread, it.pan.x, it.pan.y, it.yawDeg) },
            restore = { values ->
                DeckTransformState(
                    initialZoom = values[0],
                    initialSpread = values[1],
                    initialPan = Offset(values[2], values[3]),
                    initialYaw = values[4],
                )
            },
        )
    }
}

/** Remembers a [DeckTransformState] across configuration changes and process death. */
@androidx.compose.runtime.Composable
fun rememberDeckTransformState(): DeckTransformState =
    rememberSaveable(saver = DeckTransformState.Saver) { DeckTransformState() }
