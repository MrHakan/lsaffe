package com.deckwatch.feature.deckview.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanShape
import com.deckwatch.core.testing.TestData
import com.deckwatch.feature.deckview.geometry.HitTarget
import com.deckwatch.feature.deckview.model.RenderModelAssembler
import com.deckwatch.feature.deckview.render.StackLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The tap-priority half of §7.2's gesture table, resolved against a real [StackLayout].
 *
 * The layout is built at angle 0 — the flat plan — so the expected pixels are arithmetic rather than
 * trigonometry, and the priority rule (marker over deck, topmost deck over the ones under it) is
 * what is actually under test.
 */
class DeckHitResolutionTest {

    private val plan = DeckPlan(shape = PlanShape.RECTANGLE)

    private val model = RenderModelAssembler.assemble(
        vesselId = "vessel-1",
        vesselName = "MV Example",
        decks = listOf(
            TestData.deck(id = "upper", vesselId = "vessel-1", levelIndex = 0, plan = plan),
            TestData.deck(id = "bridge", vesselId = "vessel-1", levelIndex = 10, plan = plan),
        ),
        zonesByDeck = emptyMap(),
        equipment = listOf(
            TestData.equipment(id = "e-upper", deckId = "upper", posX = 0.5f, posY = 0.5f),
            TestData.equipment(id = "e-bridge", deckId = "bridge", posX = 0.5f, posY = 0.5f),
        ),
        typeNames = mapOf("FFE_PORTABLE_EXTINGUISHER" to "Portable fire extinguisher"),
        today = TestData.referenceDay,
    )

    private val layout = StackLayout.of(
        viewport = Size(VIEWPORT, VIEWPORT),
        planSizePx = PLAN_SIZE,
        deckHeightPx = DECK_HEIGHT,
        deckCount = 2,
        angleDeg = 0f,
        zoom = 1f,
        spread = 1f,
        pan = Offset.Zero,
    )

    /** Rank 0 sits half a deck height below the viewport centre so the stack is centred. */
    private val groundOrigin = Offset(VIEWPORT / 2f, VIEWPORT / 2f + DECK_HEIGHT / 2f)

    @Test
    fun `a tap on a marker beats the deck surface under it`() {
        val target = resolve(layout, model.decks, groundOrigin, HIT_RADIUS)

        assertThat(target).isInstanceOf(HitTarget.Marker::class.java)
        assertThat((target as HitTarget.Marker).equipmentId).isEqualTo("e-upper")
        assertThat(target.deckId).isEqualTo("upper")
    }

    @Test
    fun `a marker on the upper deck wins over the lower deck it overlaps`() {
        val bridgeCentre = Offset(groundOrigin.x, groundOrigin.y - DECK_HEIGHT)

        val target = resolve(layout, model.decks, bridgeCentre, HIT_RADIUS)

        assertThat((target as HitTarget.Marker).equipmentId).isEqualTo("e-bridge")
    }

    @Test
    fun `a tap on bare plan resolves to the topmost deck containing it`() {
        // Inside both outlines: the bridge is drawn last, so it takes the tap.
        val overlapping = Offset(groundOrigin.x + 200f, groundOrigin.y)

        val target = resolve(layout, model.decks, overlapping, HIT_RADIUS)

        assertThat(target).isInstanceOf(HitTarget.Surface::class.java)
        assertThat((target as HitTarget.Surface).deckId).isEqualTo("bridge")
    }

    @Test
    fun `a tap below the upper deck only resolves to the upper deck`() {
        // Below the bridge's outline but still inside the upper deck's.
        val lowerOnly = Offset(groundOrigin.x, groundOrigin.y + PLAN_SIZE / 2f - 10f)

        val target = resolve(layout, model.decks, lowerOnly, HIT_RADIUS)

        assertThat((target as HitTarget.Surface).deckId).isEqualTo("upper")
    }

    @Test
    fun `plan coordinates come back with the surface hit`() {
        // 0.4 of the plan aft of the centre line, below the bridge's outline so the upper deck owns
        // the tap: at angle 0 that is exactly 0.4 x planSize pixels down the screen.
        val aft = Offset(groundOrigin.x, groundOrigin.y + PLAN_SIZE * 0.4f)

        val target = resolve(layout, model.decks, aft, HIT_RADIUS) as HitTarget.Surface

        assertThat(target.deckId).isEqualTo("upper")
        assertThat(target.plan.x).isWithin(1e-3f).of(0.5f)
        assertThat(target.plan.y).isWithin(1e-3f).of(0.9f)
    }

    @Test
    fun `a tap on empty space hits nothing`() {
        val target = resolve(layout, model.decks, Offset(5f, 5f), HIT_RADIUS)

        assertThat(target).isEqualTo(HitTarget.None)
    }

    @Test
    fun `no layout yet means no target`() {
        assertThat(resolve(null, model.decks, groundOrigin, HIT_RADIUS)).isEqualTo(HitTarget.None)
        assertThat(resolve(layout, emptyList(), groundOrigin, HIT_RADIUS)).isEqualTo(HitTarget.None)
    }

    private companion object {
        const val VIEWPORT = 1000f
        const val PLAN_SIZE = 500f
        const val DECK_HEIGHT = 64f
        const val HIT_RADIUS = 26f
    }
}
