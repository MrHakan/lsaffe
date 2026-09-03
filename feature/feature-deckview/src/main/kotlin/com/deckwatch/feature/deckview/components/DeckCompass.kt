package com.deckwatch.feature.deckview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.material3.MaterialTheme
import com.deckwatch.core.designsystem.theme.Dimens
import com.deckwatch.feature.deckview.R
import com.deckwatch.feature.deckview.geometry.IsoProjection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The compass strip of §7.2 — a heading tape under the deck stack that turns the vessel.
 *
 * ### Why it reads a ship-relative bearing and not a magnetic one
 *
 * A deck plan has no north. It is drawn bow-up (`bowAtTop`), and nothing in the register records
 * which way the ship was pointing when it was drawn, so a tape marked N/E/S/W would be inventing a
 * heading the app cannot know. The four marks are therefore the four the officer actually uses
 * aboard — bow, starboard, stern, port — and the numbers are relative bearings from the bow, in
 * the three-digit form they are spoken in. 000 is dead ahead, 090 is the starboard beam.
 *
 * ### Direct manipulation
 *
 * The tape follows the finger: drag right and the tape slides right, which brings the bearings to
 * port of the mark under it and turns the ship clockwise on screen. That is the same relationship a
 * paper chart has with the hand moving it, and it is why the drag is not inverted — the officer is
 * pushing the vessel round, not steering a camera.
 *
 * A double tap levels the ship, because a tape can be dragged a long way and there has to be one
 * gesture that means "bow back to the top".
 *
 * ### Cost
 *
 * The bearing is read through [yawDeg] *inside* the draw lambda and the drag handler, never during
 * composition, so turning the ship invalidates the draw phase of this strip and the deck canvas and
 * recomposes nothing — the same discipline the canvas keeps for pan and pinch.
 *
 * @param yawDeg the live yaw, read per frame rather than passed by value.
 * @param onTurn called with a yaw delta in degrees, clockwise-positive.
 * @param onLevel put the bow back at the top.
 */
@Composable
fun DeckCompass(
    yawDeg: () -> Float,
    onTurn: (deltaDeg: Float) -> Unit,
    onLevel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current

    val cardinals = listOf(
        stringResource(R.string.compass_bow),
        stringResource(R.string.compass_starboard),
        stringResource(R.string.compass_stern),
        stringResource(R.string.compass_port),
    )
    val label = stringResource(R.string.compass_label)
    val turnToPort = stringResource(R.string.compass_action_port)
    val turnToStarboard = stringResource(R.string.compass_action_starboard)
    val levelAction = stringResource(R.string.compass_action_level)

    val majorStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface)
    val cardinalStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.primary)

    val palette = remember(scheme) {
        CompassPalette(
            ground = scheme.surfaceContainerHighest.copy(alpha = GROUND_ALPHA),
            minorTick = scheme.onSurfaceVariant.copy(alpha = MINOR_TICK_ALPHA),
            majorTick = scheme.onSurfaceVariant,
            mark = scheme.primary,
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpacingL)
            .height(StripHeight)
            .semantics {
                contentDescription = label
                // Read inside the semantics lambda, so the announced value follows the drag
                // without the strip recomposing for every frame of it.
                stateDescription = bearingSpeech(IsoProjection.bearingFor(yawDeg()), cardinals)
                customActions = listOf(
                    CustomAccessibilityAction(turnToPort) {
                        onTurn(-STEP_DEG)
                        true
                    },
                    CustomAccessibilityAction(turnToStarboard) {
                        onTurn(STEP_DEG)
                        true
                    },
                    CustomAccessibilityAction(levelAction) {
                        onLevel()
                        true
                    },
                )
            }
            // One input node with both detectors as siblings: two `pointerInput` modifiers would
            // race for the same pointer, and the drag's consume would eat the second tap.
            .pointerInput(Unit) {
                coroutineScope {
                    launch {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            // Read the width per drag rather than at setup, so a rotation or a
                            // split-screen resize does not leave the tape geared to the old width.
                            onTurn(dragAmount * VISIBLE_SPAN_DEG / size.width.coerceAtLeast(1))
                        }
                    }
                    launch {
                        detectTapGestures(
                            onDoubleTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLevel()
                            },
                        )
                    }
                }
            },
    ) {
        drawCompass(
            bearing = IsoProjection.bearingFor(yawDeg()),
            palette = palette,
            measurer = measurer,
            majorStyle = majorStyle,
            cardinalStyle = cardinalStyle,
            cardinals = cardinals,
        )
    }
}

private data class CompassPalette(
    val ground: Color,
    val minorTick: Color,
    val majorTick: Color,
    val mark: Color,
)

/**
 * Draws the tape.
 *
 * Every tick is placed by the signed shortest way round from the current bearing, so the tape wraps
 * through the bow without a seam and without any modular arithmetic at the call site.
 */
private fun DrawScope.drawCompass(
    bearing: Float,
    palette: CompassPalette,
    measurer: TextMeasurer,
    majorStyle: TextStyle,
    cardinalStyle: TextStyle,
    cardinals: List<String>,
) {
    if (size.width <= 0f) return
    val centreX = size.width / 2f
    val pixelsPerDegree = size.width / VISIBLE_SPAN_DEG
    val baseline = size.height - TickBase.toPx()

    drawRoundRect(color = palette.ground, cornerRadius = CornerRadius(size.height / 2f))

    var tick = 0
    while (tick < FULL_TURN) {
        val delta = IsoProjection.signedDelta(tick.toFloat(), bearing)
        if (abs(delta) <= VISIBLE_SPAN_DEG / 2f + TICK_MARGIN_DEG) {
            val x = centreX + delta * pixelsPerDegree
            val isCardinal = tick % QUARTER_TURN == 0
            val isMajor = tick % MAJOR_STEP == 0
            // Ticks fade out towards the ends of the strip rather than being clipped, so the tape
            // reads as continuous instead of stopping at a hard edge.
            val fade = (1f - abs(delta) / (VISIBLE_SPAN_DEG / 2f)).coerceIn(0f, 1f)

            when {
                isCardinal -> drawTick(x, baseline, CardinalTickHeight, palette.mark, fade)
                isMajor -> drawTick(x, baseline, MajorTickHeight, palette.majorTick, fade)
                else -> drawTick(x, baseline, MinorTickHeight, palette.minorTick, fade)
            }

            if (isCardinal || isMajor) {
                val text = if (isCardinal) cardinals[tick / QUARTER_TURN] else formatBearing(tick)
                val style = if (isCardinal) cardinalStyle else majorStyle
                val measured = measurer.measure(text, style)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = x - measured.size.width / 2f,
                        y = baseline - CardinalTickHeight.toPx() - measured.size.height,
                    ),
                    alpha = fade,
                )
            }
        }
        tick += MINOR_STEP
    }

    drawMark(centreX, palette.mark)
}

private fun DrawScope.drawTick(x: Float, baseline: Float, height: Dp, color: Color, alpha: Float) {
    val h = height.toPx()
    drawLine(
        color = color,
        start = Offset(x, baseline),
        end = Offset(x, baseline - h),
        strokeWidth = TickWidth.toPx(),
        alpha = alpha,
    )
}

/**
 * The mark the bearing is read against: a caret under the tape, pointing up at it.
 *
 * Under rather than over, because a mark drawn on top of the tape would cover the very tick the
 * officer is trying to read.
 */
private fun DrawScope.drawMark(centreX: Float, color: Color) {
    val width = MarkWidth.toPx()
    val height = MarkHeight.toPx()
    val tipY = size.height - TickBase.toPx()
    val path = Path().apply {
        moveTo(centreX, tipY)
        lineTo(centreX - width / 2f, tipY + height)
        lineTo(centreX + width / 2f, tipY + height)
        close()
    }
    drawPath(path, color)
}

/** Relative bearings are spoken and written in three digits: 5° to starboard is "005". */
internal fun formatBearing(degrees: Int): String {
    val wrapped = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
    return wrapped.toString().padStart(BEARING_DIGITS, '0')
}

/**
 * What a screen reader says: the three-digit bearing, and the quarter it falls in so the number
 * means something without a picture.
 */
internal fun bearingSpeech(bearing: Float, cardinals: List<String>): String {
    val rounded = bearing.roundToInt() % FULL_TURN
    val nearest = ((rounded + QUARTER_TURN / 2) / QUARTER_TURN) % CARDINAL_COUNT
    return "${formatBearing(rounded)} · ${cardinals[nearest]}"
}

/** How much of the full turn the strip shows at once. */
internal const val VISIBLE_SPAN_DEG: Float = 150f

/** One accessibility action's worth of turn. */
internal const val STEP_DEG: Float = 45f

private const val FULL_TURN = 360
private const val QUARTER_TURN = 90
private const val MAJOR_STEP = 30
private const val MINOR_STEP = 10
private const val CARDINAL_COUNT = 4
private const val BEARING_DIGITS = 3
private const val TICK_MARGIN_DEG = 10f
private const val GROUND_ALPHA = 0.85f
private const val MINOR_TICK_ALPHA = 0.55f

private val StripHeight = 48.dp
private val TickBase = 8.dp
private val MinorTickHeight = 6.dp
private val MajorTickHeight = 10.dp
private val CardinalTickHeight = 13.dp
private val TickWidth = 1.5.dp
private val MarkWidth = 12.dp
private val MarkHeight = 8.dp
