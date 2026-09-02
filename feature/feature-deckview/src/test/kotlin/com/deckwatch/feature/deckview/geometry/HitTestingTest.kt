package com.deckwatch.feature.deckview.geometry

import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Hit-testing, zoom clamps, grid snapping and the stack ordering rule of §6.2. */
class HitTestingTest {

    private val markers = listOf(
        ScreenMarker("near", Vec2(100f, 100f)),
        ScreenMarker("far", Vec2(140f, 100f)),
        ScreenMarker("distant", Vec2(600f, 600f)),
    )

    @Test
    fun `nearest marker wins inside the touch radius`() {
        val hit = HitTesting.nearestMarker(markers, Vec2(115f, 100f), radiusPx = 40f)

        assertThat(hit?.id).isEqualTo("near")
    }

    @Test
    fun `a touch closer to the second marker resolves to it`() {
        val hit = HitTesting.nearestMarker(markers, Vec2(132f, 100f), radiusPx = 40f)

        assertThat(hit?.id).isEqualTo("far")
    }

    @Test
    fun `nothing is picked outside the touch radius`() {
        assertThat(HitTesting.nearestMarker(markers, Vec2(300f, 300f), radiusPx = 40f)).isNull()
        assertThat(HitTesting.nearestMarker(markers, Vec2(100f, 100f), radiusPx = 0f)).isNull()
    }

    @Test
    fun `the topmost deck under the point wins`() {
        val lower = ScreenDeck("lower", levelZ = 0, outline = square(0f, 0f, 400f))
        val upper = ScreenDeck("upper", levelZ = 3, outline = square(0f, 0f, 400f))

        val hit = HitTesting.deckAt(listOf(lower, upper), Vec2(200f, 200f))

        assertThat(hit?.id).isEqualTo("upper")
    }

    @Test
    fun `a point outside every outline hits no deck`() {
        val deck = ScreenDeck("only", levelZ = 0, outline = square(0f, 0f, 100f))

        assertThat(HitTesting.deckAt(listOf(deck), Vec2(400f, 400f))).isNull()
    }

    @Test
    fun `decks are ranked by levelIndex with gaps of ten collapsed to consecutive ranks`() {
        val decks = listOf(
            TestData.deck(id = "bridge", levelIndex = 20),
            TestData.deck(id = "upper", levelIndex = 0),
            TestData.deck(id = "engine", levelIndex = -10),
        )

        val ordered = DeckStackOrder.bottomFirst(decks)

        assertThat(ordered.map { it.deck.id }).containsExactly("engine", "upper", "bridge").inOrder()
        assertThat(ordered.map { it.levelZ }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `top first is the spine order`() {
        val decks = listOf(
            TestData.deck(id = "upper", levelIndex = 0),
            TestData.deck(id = "bridge", levelIndex = 30),
        )

        assertThat(DeckStackOrder.topFirst(decks).map { it.deck.id })
            .containsExactly("bridge", "upper").inOrder()
    }

    @Test
    fun `zoom is clamped per mode`() {
        assertThat(ZoomLimits.clamp(0.1f, deckMode = false)).isEqualTo(0.4f)
        assertThat(ZoomLimits.clamp(99f, deckMode = false)).isEqualTo(4f)
        assertThat(ZoomLimits.clamp(99f, deckMode = true)).isEqualTo(6f)
        assertThat(ZoomLimits.clamp(0.1f, deckMode = true)).isEqualTo(0.4f)
        assertThat(ZoomLimits.clamp(2.5f, deckMode = false)).isEqualTo(2.5f)
    }

    @Test
    fun `grid snapping rounds to the nearest line and clamps into the plan`() {
        assertThat(GridSnap.snapCoordinate(0.51f)).isWithin(TOLERANCE).of(0.5f)
        assertThat(GridSnap.snapCoordinate(0.58f)).isWithin(TOLERANCE).of(0.6f)
        assertThat(GridSnap.snapCoordinate(-0.4f)).isWithin(TOLERANCE).of(0f)
        assertThat(GridSnap.snapCoordinate(1.4f)).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun `snapping off still clamps but does not move the point`() {
        val free = GridSnap.snap(Vec2(0.5312f, 0.1234f), enabled = false)

        assertThat(free.x).isWithin(TOLERANCE).of(0.5312f)
        assertThat(free.y).isWithin(TOLERANCE).of(0.1234f)
    }

    @Test
    fun `snapping on moves the point onto the grid`() {
        val snapped = GridSnap.snap(Vec2(0.5312f, 0.1234f), enabled = true)

        assertThat(snapped.x).isWithin(TOLERANCE).of(0.55f)
        assertThat(snapped.y).isWithin(TOLERANCE).of(0.1f)
    }

    private fun square(left: Float, top: Float, edge: Float): List<Vec2> = listOf(
        Vec2(left, top),
        Vec2(left + edge, top),
        Vec2(left + edge, top + edge),
        Vec2(left, top + edge),
    )

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
