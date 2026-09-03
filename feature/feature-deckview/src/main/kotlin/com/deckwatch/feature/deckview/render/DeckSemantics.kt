package com.deckwatch.feature.deckview.render

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.deckwatch.feature.deckview.model.StackRenderModel
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The canvas's semantics tree — §14 "full TalkBack labelling including the deck canvas".
 *
 * A `Canvas` has no children, so the tree is supplied as an overlay of invisible, semantics-only
 * boxes placed at the same pixels the renderer draws. They carry no pointer input, so touches fall
 * straight through to the gesture layer beneath, and their positions are computed in the **layout**
 * phase from [transform], so panning and zooming re-place them without recomposing anything.
 *
 * Markers are exposed for the deck the officer is working on rather than for all six hundred at
 * once: a flat list of hundreds of nodes is unusable under TalkBack, and §7.1C guarantees LIST mode
 * as the complete non-graphical equivalent. Decks themselves are always exposed.
 */
@Composable
@Suppress("LongParameterList") // Mirrors the renderer's inputs so the two place identical pixels.
internal fun DeckSemanticsOverlay(
    model: StackRenderModel,
    transform: DeckTransformState,
    deckMode: Boolean,
    deckNodes: List<DeckSemanticNode>,
    markerNodes: List<DeckSemanticNode>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val nodes = (deckNodes + markerNodes).take(MAX_NODES)
    if (nodes.isEmpty()) return

    Layout(
        modifier = modifier,
        content = {
            for (node in nodes) {
                Box(
                    modifier = Modifier.semantics {
                        contentDescription = node.description
                        onClick(label = node.clickLabel) {
                            node.onClick()
                            true
                        }
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val edge = with(density) { NODE_SIZE.roundToPx() }
        val placeables = measurables.map { it.measure(Constraints.fixed(edge, edge)) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            val planFraction = if (deckMode) {
                DeckRenderDefaults.DECK_PLAN_FRACTION
            } else {
                DeckRenderDefaults.STACK_PLAN_FRACTION
            }
            val stackLayout = StackLayout.of(
                viewport = Size(width.toFloat(), height.toFloat()),
                planSizePx = min(width, height) * planFraction,
                deckHeightPx = if (deckMode) {
                    0f
                } else {
                    with(density) { DeckRenderDefaults.DeckHeight.toPx() }
                },
                deckCount = if (deckMode) 1 else model.decks.size,
                angleDeg = transform.angleDeg,
                zoom = transform.zoom,
                spread = transform.spread,
                pan = transform.pan,
                yawDeg = transform.yawDeg,
            )
            placeables.forEachIndexed { index, placeable ->
                val node = nodes[index]
                val centre = stackLayout.toScreen(node.levelZ, node.planX, node.planY)
                placeable.place(
                    x = (centre.x - edge / 2f).roundToInt().coerceIn(-edge, width),
                    y = (centre.y - edge / 2f).roundToInt().coerceIn(-edge, height),
                )
            }
        }
    }
}

/**
 * Spells a tag out for TalkBack: `FE-UD-03` is read "F E - U D - 0 3" rather than as a word (§14).
 */
internal fun spellTag(tag: String): String = tag.toCharArray().joinToString(" ")

private val NODE_SIZE = 48.dp

/** A hard ceiling on semantics nodes; LIST mode is the complete equivalent past it (§7.1C). */
private const val MAX_NODES = 256
