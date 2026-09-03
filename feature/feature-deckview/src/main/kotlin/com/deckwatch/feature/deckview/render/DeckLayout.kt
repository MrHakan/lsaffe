package com.deckwatch.feature.deckview.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.deckwatch.feature.deckview.geometry.IsoProjection
import com.deckwatch.feature.deckview.geometry.ScreenDeck
import com.deckwatch.feature.deckview.geometry.ScreenMarker
import com.deckwatch.feature.deckview.geometry.Vec2
import com.deckwatch.feature.deckview.model.DeckNode
import com.deckwatch.feature.deckview.model.StackRenderModel

/**
 * Where the stack sits on screen for one frame.
 *
 * Derived cheaply from the viewport, the render model and [DeckTransformState]; the canvas builds it
 * per draw and the gesture layer reads the last one built, so both agree on exactly the same pixels.
 */
data class StackLayout(
    val projection: IsoProjection,
    /** The projection used for cached paths: same angle, zoom-independent scale (§7.2 path cache). */
    val basePathProjection: IsoProjection,
    val zoom: Float,
    /** Screen position of the rank-0 deck's centre. */
    val origin: Offset,
    val viewport: Size,
) {
    /** The screen centre of the deck at rank [levelZ]. */
    fun deckOrigin(levelZ: Int): Offset =
        Offset(origin.x, origin.y - levelZ * projection.levelStepPx)

    /** Where a plan point on the deck at rank [levelZ] lands on screen. */
    fun toScreen(levelZ: Int, planX: Float, planY: Float): Offset {
        val projected = projection.project(planX, planY, levelZ)
        return Offset(origin.x + projected.x, origin.y + projected.y)
    }

    fun toScreen(levelZ: Int, plan: Vec2): Offset = toScreen(levelZ, plan.x, plan.y)

    /** The inverse: a screen point back to plan coordinates on the deck at rank [levelZ]. */
    fun toPlan(levelZ: Int, screen: Offset): Vec2 =
        projection.unproject(screen.x - origin.x, screen.y - origin.y, levelZ)

    /** The deck's projected outline in screen space — the polygon a tap is tested against. */
    fun screenOutline(deck: DeckNode): List<Vec2> =
        deck.outline.map { point ->
            val screen = toScreen(deck.levelZ, point)
            Vec2(screen.x, screen.y)
        }

    /** Every interactive deck as a hit-test polygon. */
    fun screenDecks(decks: List<DeckNode>): List<ScreenDeck> =
        decks.map { ScreenDeck(it.deckId, it.levelZ, screenOutline(it)) }

    /** Every marker on [deck] as a hit-test point. */
    fun screenMarkers(deck: DeckNode): List<ScreenMarker> =
        deck.markers.map { marker ->
            val screen = toScreen(deck.levelZ, marker.position)
            ScreenMarker(marker.equipmentId, Vec2(screen.x, screen.y))
        }

    companion object {

        /**
         * Builds the layout for one frame.
         *
         * @param planSizePx how many pixels one unit of plan space spans at zoom 1 — the deck's
         *   on-screen size, derived from the viewport so a plan fills a phone and a tablet alike.
         * @param deckHeightPx §7.2's constant screen-space deck separation (64dp) at zoom 1.
         * @param deckCount used to centre the stack vertically, so a twenty-deck vessel opens with
         *   its middle deck under the thumb rather than off the top of the screen.
         * @param yawDeg how far the compass has turned the vessel about its own centre.
         */
        @Suppress("LongParameterList") // Every argument is an independent frame input.
        fun of(
            viewport: Size,
            planSizePx: Float,
            deckHeightPx: Float,
            deckCount: Int,
            angleDeg: Float,
            zoom: Float,
            spread: Float,
            pan: Offset,
            yawDeg: Float = IsoProjection.NO_YAW_DEG,
        ): StackLayout {
            val projection = IsoProjection(
                angleDeg = angleDeg,
                scale = planSizePx * zoom,
                deckHeightPx = deckHeightPx * zoom,
                spread = spread,
                yawDeg = yawDeg,
            )
            val stackCentre = (deckCount - 1).coerceAtLeast(0) * projection.levelStepPx / 2f
            return StackLayout(
                projection = projection,
                basePathProjection = IsoProjection(
                    angleDeg = angleDeg,
                    scale = planSizePx,
                    deckHeightPx = 0f,
                    spread = 1f,
                    yawDeg = yawDeg,
                ),
                zoom = zoom,
                origin = Offset(
                    x = viewport.width / 2f + pan.x,
                    y = viewport.height / 2f + stackCentre + pan.y,
                ),
                viewport = viewport,
            )
        }

        /**
         * The pan that centres the deck at rank [levelZ] in the viewport — the spine's fly-to target
         * and the double-tap zoom-to-fit of §7.2.
         */
        fun panToCentre(levelZ: Int, deckCount: Int, levelStepPx: Float): Offset {
            val stackCentre = (deckCount - 1).coerceAtLeast(0) * levelStepPx / 2f
            return Offset(0f, levelZ * levelStepPx - stackCentre)
        }
    }
}

/** Which decks a frame actually draws, and how transparent each one is — §7.2 occlusion and focus. */
object DeckVisibility {

    /** Decks above the focused one render at 25 % alpha and are non-interactive. */
    const val ABOVE_ALPHA: Float = 0.25f

    /** Decks below render at 60 %. */
    const val BELOW_ALPHA: Float = 0.60f

    fun alphaFor(deck: DeckNode, focusedDeckId: String?, model: StackRenderModel): Float {
        val focused = model.deck(focusedDeckId) ?: return 1f
        return when {
            deck.deckId == focused.deckId -> 1f
            deck.levelZ > focused.levelZ -> ABOVE_ALPHA
            else -> BELOW_ALPHA
        }
    }

    /** A deck above the focused one is not interactive (§7.2). */
    fun isInteractive(deck: DeckNode, focusedDeckId: String?, model: StackRenderModel): Boolean {
        val focused = model.deck(focusedDeckId) ?: return true
        return deck.levelZ <= focused.levelZ
    }

    /** The decks a frame draws: one in deck mode, one while isolated, otherwise the whole stack. */
    fun visibleDecks(
        model: StackRenderModel,
        deckMode: Boolean,
        activeDeckId: String?,
        isolatedDeckId: String?,
    ): List<DeckNode> = when {
        isolatedDeckId != null -> model.decks.filter { it.deckId == isolatedDeckId }
        deckMode -> model.decks.filter { it.deckId == activeDeckId }
        else -> model.decks
    }
}
