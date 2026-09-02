package com.deckwatch.feature.deckview.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import com.deckwatch.feature.deckview.geometry.GridSnap
import com.deckwatch.feature.deckview.geometry.HitTarget
import com.deckwatch.feature.deckview.geometry.HitTesting
import com.deckwatch.feature.deckview.geometry.Polygons
import com.deckwatch.feature.deckview.geometry.ScreenMarker
import com.deckwatch.feature.deckview.geometry.Vec2
import com.deckwatch.feature.deckview.model.DeckNode
import com.deckwatch.feature.deckview.render.DeckLayoutHolder
import com.deckwatch.feature.deckview.render.DeckTransformState
import com.deckwatch.feature.deckview.render.MarkerDrag
import com.deckwatch.feature.deckview.render.StackLayout
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The whole §7.2 gesture table in one detector.
 *
 * One `awaitEachGesture` owns every touch, rather than a stack of `detectTapGestures` /
 * `detectTransformGestures` modifiers racing each other: with several detectors on one node a
 * long-press-then-drag and a two-finger fan starve one another, which on a deck plan means a dropped
 * marker or a stuck pan. Here the first phase classifies the gesture — second pointer, slop, long
 * press or lift, whichever comes first — and the second phase runs exactly one behaviour to
 * completion.
 *
 * | Gesture | Stack mode | Deck mode |
 * |---|---|---|
 * | Drag | pan | pan |
 * | Pinch | zoom 0.4×–4× | zoom 0.4×–6× |
 * | Two-finger vertical drag | fan spread 0.5×–3× | — |
 * | Tap deck surface | focus / enter deck mode | deselect |
 * | Tap marker | equipment sheet | equipment sheet |
 * | Long-press marker | pick up and drag, with haptic | same |
 * | Long-press empty plan | isolate the deck, then "add equipment here" | same |
 * | Double tap | zoom to fit | zoom to fit |
 *
 * §7.2 asks both that a long press isolate the deck it lands on *and* that a long press on empty
 * plan offer "add equipment here". Those are one gesture, so they are one behaviour: holding fades
 * the other decks away so the officer can see where the marker will go, and lifting opens the add
 * sheet at that coordinate.
 *
 * A marker tap fires on lift without waiting out the double-tap window — opening the condition sheet
 * is the most-used action in the app (§7.3) and must not feel laggy. Only taps on the plan itself
 * wait, because only there is a double tap meaningful.
 */
@Suppress("LongParameterList") // A gesture surface: each argument is a distinct input or sink.
fun Modifier.deckGestures(
    key: Any?,
    transform: DeckTransformState,
    layoutHolder: DeckLayoutHolder,
    interactiveDecks: () -> List<DeckNode>,
    deckMode: Boolean,
    gridSnapEnabled: Boolean,
    hitRadiusPx: Float,
    performHaptic: () -> Unit,
    callbacks: DeckGestureCallbacks,
): Modifier = pointerInput(key, deckMode, gridSnapEnabled, hitRadiusPx) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val layout = layoutHolder.layout
        val decks = interactiveDecks()
        val target = resolve(layout, decks, down.position, hitRadiusPx)

        when (decide(down, touchSlop, longPressTimeout)) {
            Decision.Transform -> runTransform(transform, deckMode)

            Decision.Pan -> runPan(transform, deckMode)

            Decision.Tap -> when (target) {
                is HitTarget.Marker -> callbacks.onTapMarker(target.equipmentId, target.deckId)

                is HitTarget.Surface ->
                    if (awaitSecondTap(doubleTapTimeout)) {
                        callbacks.onZoomToFit(target.deckId)
                    } else {
                        callbacks.onTapDeck(target.deckId)
                    }

                HitTarget.None ->
                    if (awaitSecondTap(doubleTapTimeout)) {
                        callbacks.onZoomToFit(null)
                    } else {
                        callbacks.onTapEmpty()
                    }
            }

            Decision.LongPress -> when (target) {
                is HitTarget.Marker -> {
                    performHaptic()
                    callbacks.onMarkerPickedUp(target.equipmentId, target.deckId)
                    runMarkerDrag(target, layout, decks, transform, gridSnapEnabled, callbacks)
                }

                is HitTarget.Surface -> {
                    performHaptic()
                    runIsolateThenAdd(target, transform, touchSlop, callbacks)
                }

                HitTarget.None -> consumeUntilUp()
            }

            Decision.Cancelled -> Unit
        }
    }
}

// --------------------------------------------------------------------- phase 1: classify

private enum class Decision { Tap, Pan, Transform, LongPress, Cancelled }

/**
 * Classifies the gesture: a second finger, movement past the touch slop, a lift, or the long-press
 * deadline — whichever happens first.
 */
private suspend fun AwaitPointerEventScope.decide(
    down: PointerInputChange,
    touchSlop: Float,
    longPressTimeoutMillis: Long,
): Decision = try {
    withTimeout(longPressTimeoutMillis) {
        var decision: Decision? = null
        while (decision == null) {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            val change = event.changes.firstOrNull { it.id == down.id }
            decision = when {
                pressedCount > 1 -> Decision.Transform
                change == null -> Decision.Cancelled
                !change.pressed -> Decision.Tap
                (change.position - down.position).getDistance() > touchSlop -> Decision.Pan
                else -> null
            }
        }
        decision
    }
} catch (_: PointerEventTimeoutCancellationException) {
    Decision.LongPress
}

/** True when a second tap arrives inside the double-tap window (§7.2 "double-tap → zoom to fit"). */
private suspend fun AwaitPointerEventScope.awaitSecondTap(doubleTapTimeoutMillis: Long): Boolean {
    val second = withTimeoutOrNull(doubleTapTimeoutMillis) {
        awaitFirstDown(requireUnconsumed = false)
    } ?: return false
    second.consume()
    consumeUntilUp()
    return true
}

// --------------------------------------------------------------------- phase 2: behaviours

private suspend fun AwaitPointerEventScope.runPan(transform: DeckTransformState, deckMode: Boolean) {
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        when {
            pressed.isEmpty() -> return
            pressed.size > 1 -> {
                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                runTransform(transform, deckMode)
                return
            }

            else -> pressed.first().let { change ->
                transform.panBy(change.positionChange())
                change.consume()
            }
        }
    }
}

/**
 * Two fingers: pinch to zoom, or a vertical drag to fan the stack apart (§7.2).
 *
 * Which of the two it is, is decided once — from whichever passes its threshold first — and then
 * held for the rest of the gesture, so the fan never flickers into a zoom halfway through.
 */
private suspend fun AwaitPointerEventScope.runTransform(
    transform: DeckTransformState,
    deckMode: Boolean,
) {
    var mode = TwoFinger.UNDECIDED
    var zoomAccumulator = 1f
    var panAccumulator = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) return
        if (pressed.size < 2) {
            // Down to one finger: keep panning rather than dropping the gesture on the floor.
            pressed.first().let { change ->
                transform.panBy(change.positionChange())
                change.consume()
            }
            continue
        }
        val zoomChange = event.zoomChange()
        val panChange = event.panChange()
        zoomAccumulator *= zoomChange
        panAccumulator += panChange
        if (mode == TwoFinger.UNDECIDED) {
            mode = classify(zoomAccumulator, panAccumulator, deckMode)
        }
        if (mode == TwoFinger.SPREAD) {
            transform.updateSpread(transform.spread + panChange.y / SPREAD_PIXELS_PER_UNIT)
        } else {
            transform.zoomBy(zoomChange, deckMode)
            transform.panBy(panChange)
        }
        event.changes.fastForEach { if (it.positionChanged()) it.consume() }
    }
}

private enum class TwoFinger { UNDECIDED, ZOOM, SPREAD }

private fun classify(zoom: Float, pan: Offset, deckMode: Boolean): TwoFinger = when {
    abs(zoom - 1f) > ZOOM_THRESHOLD -> TwoFinger.ZOOM
    !deckMode && abs(pan.y) > SPREAD_THRESHOLD_PX && abs(pan.y) > abs(pan.x) * SPREAD_AXIS_RATIO ->
        TwoFinger.SPREAD

    abs(pan.x) > SPREAD_THRESHOLD_PX || abs(pan.y) > SPREAD_THRESHOLD_PX -> TwoFinger.ZOOM
    else -> TwoFinger.UNDECIDED
}

/**
 * The marker pick-up of §7.2: the marker follows the finger, snapping to the grid when the officer
 * has that on, and a drop outside the outline is refused so the caller can shake it home.
 */
@Suppress("LongParameterList") // Drag needs the whole frame context to stay pixel-accurate.
private suspend fun AwaitPointerEventScope.runMarkerDrag(
    target: HitTarget.Marker,
    layout: StackLayout?,
    decks: List<DeckNode>,
    transform: DeckTransformState,
    gridSnapEnabled: Boolean,
    callbacks: DeckGestureCallbacks,
) {
    val deck = decks.firstOrNull { it.deckId == target.deckId }
    val start = currentPosition()
    if (layout == null || deck == null || start == null) {
        consumeUntilUp()
        return
    }

    fun dragAt(position: Offset): MarkerDrag {
        val raw = layout.toPlan(deck.levelZ, position)
        val snapped = GridSnap.snap(raw, gridSnapEnabled)
        return MarkerDrag(
            equipmentId = target.equipmentId,
            deckId = deck.deckId,
            levelZ = deck.levelZ,
            planX = snapped.x,
            planY = snapped.y,
            insideOutline = Polygons.contains(deck.outline, raw),
        )
    }

    var drag = dragAt(start)
    transform.drag = drag
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        change.consume()
        if (!change.pressed) break
        drag = dragAt(change.position)
        transform.drag = drag
    }
    transform.drag = null
    callbacks.onMarkerDropped(
        drag.equipmentId,
        drag.deckId,
        drag.planX,
        drag.planY,
        drag.insideOutline,
    )
}

/**
 * Long press on the plan: isolate the deck while the finger is down (§7.2 "a long-press on any deck
 * isolates it … until released"), then offer "add equipment here" at that coordinate (§7.5).
 */
private suspend fun AwaitPointerEventScope.runIsolateThenAdd(
    target: HitTarget.Surface,
    transform: DeckTransformState,
    touchSlop: Float,
    callbacks: DeckGestureCallbacks,
) {
    transform.isolatedDeckId = target.deckId
    val start = currentPosition() ?: Offset.Zero
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        if ((change.position - start).getDistance() > touchSlop) moved = true
        change.consume()
        if (!change.pressed) break
    }
    transform.isolatedDeckId = null
    if (!moved) callbacks.onAddEquipmentAt(target.deckId, target.plan.x, target.plan.y)
}

private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.fastForEach { it.consume() }
        if (event.changes.fastAll { !it.pressed }) return
    }
}

private fun AwaitPointerEventScope.currentPosition(): Offset? =
    currentEvent.changes.firstOrNull { it.pressed }?.position

// --------------------------------------------------------------------- multi-touch maths

/**
 * Pinch factor for this event.
 *
 * Compose keeps `calculateZoom` / `calculatePan` internal to `foundation`, so the two multi-touch
 * quantities the fan and the pinch need are computed here from the pointer positions directly:
 * the ratio of the mean distance-from-centroid, and the movement of the centroid.
 */
private fun PointerEvent.zoomChange(): Float {
    val current = centroidSpread(previous = false)
    val previous = centroidSpread(previous = true)
    return if (previous > 0f && current > 0f) current / previous else 1f
}

private fun PointerEvent.panChange(): Offset = centroid(previous = false) - centroid(previous = true)

private fun PointerEvent.centroid(previous: Boolean): Offset {
    var sum = Offset.Zero
    var count = 0
    changes.fastForEach { change ->
        if (change.pressed && change.previousPressed) {
            sum += if (previous) change.previousPosition else change.position
            count++
        }
    }
    return if (count == 0) Offset.Zero else sum / count.toFloat()
}

private fun PointerEvent.centroidSpread(previous: Boolean): Float {
    val centre = centroid(previous)
    var total = 0f
    var count = 0
    changes.fastForEach { change ->
        if (change.pressed && change.previousPressed) {
            val position = if (previous) change.previousPosition else change.position
            val dx = position.x - centre.x
            val dy = position.y - centre.y
            total += sqrt(dx * dx + dy * dy)
            count++
        }
    }
    return if (count == 0) 0f else total / count
}

// --------------------------------------------------------------------- hit resolution

/**
 * What the touch landed on. Markers win over deck surfaces, and among decks the topmost wins — the
 * priority §7.2's gesture table implies.
 */
internal fun resolve(
    layout: StackLayout?,
    decks: List<DeckNode>,
    position: Offset,
    hitRadiusPx: Float,
): HitTarget {
    if (layout == null || decks.isEmpty()) return HitTarget.None
    val point = Vec2(position.x, position.y)
    val owners = HashMap<String, String>()
    val markers = ArrayList<ScreenMarker>()
    decks.sortedByDescending { it.levelZ }.fastForEach { deck ->
        layout.screenMarkers(deck).fastForEach { marker ->
            if (!owners.containsKey(marker.id)) {
                owners[marker.id] = deck.deckId
                markers += marker
            }
        }
    }
    HitTesting.nearestMarker(markers, point, hitRadiusPx)?.let { hit ->
        owners[hit.id]?.let { deckId -> return HitTarget.Marker(hit.id, deckId) }
    }
    val deck = HitTesting.deckAt(layout.screenDecks(decks), point) ?: return HitTarget.None
    val node = decks.firstOrNull { it.deckId == deck.id } ?: return HitTarget.None
    return HitTarget.Surface(node.deckId, layout.toPlan(node.levelZ, position))
}

private const val ZOOM_THRESHOLD = 0.06f
private const val SPREAD_THRESHOLD_PX = 12f
private const val SPREAD_AXIS_RATIO = 1.4f

/** How many pixels of two-finger vertical drag move the fan by one whole spread unit. */
private const val SPREAD_PIXELS_PER_UNIT = 420f
