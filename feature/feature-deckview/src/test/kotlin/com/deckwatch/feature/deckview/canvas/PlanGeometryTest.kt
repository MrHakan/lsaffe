package com.deckwatch.feature.deckview.canvas

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The projection has to be exactly invertible — §7.2.
 *
 * If it is not, an item lands a few percent away from the finger that placed it, the drift grows
 * towards the edges, and nobody can say why. So the round trip is asserted at the corners and the
 * centre, at every angle the settings allow.
 */
class PlanGeometryTest {

    @Test
    fun `projecting and unprojecting returns the original point, at every angle`() {
        val points = listOf(
            PlanPoint(0f, 0f),
            PlanPoint(1f, 0f),
            PlanPoint(0f, 1f),
            PlanPoint(1f, 1f),
            PlanPoint(0.5f, 0.5f),
            PlanPoint(0.17f, 0.83f),
        )
        for (angle in listOf(0f, 10f, 20f, 30f, 35f)) {
            for (point in points) {
                val projected = PlanGeometry.project(point.x, point.y, angle)
                val back = PlanGeometry.unproject(projected.x, projected.y, angle)

                assertThat(back.x).isWithin(TOLERANCE).of(point.x)
                assertThat(back.y).isWithin(TOLERANCE).of(point.y)
            }
        }
    }

    @Test
    fun `at zero degrees the projection is the flat plan`() {
        val projected = PlanGeometry.project(0.2f, 0.9f, angleDeg = 0f)

        assertThat(projected.x).isWithin(TOLERANCE).of(0.2f)
        assertThat(projected.y).isWithin(TOLERANCE).of(0.9f)
    }

    @Test
    fun `tilting squashes the fore-and-aft axis and leans the plan`() {
        val flat = PlanGeometry.project(0.5f, 1f, angleDeg = 0f)
        val tilted = PlanGeometry.project(0.5f, 1f, angleDeg = 30f)

        assertThat(tilted.y).isLessThan(flat.y)
        assertThat(tilted.x).isGreaterThan(flat.x)
    }

    @Test
    fun `an angle beyond the settings range is clamped rather than folding the plan over`() {
        val atLimit = PlanGeometry.project(0.5f, 1f, angleDeg = 35f)
        val beyond = PlanGeometry.project(0.5f, 1f, angleDeg = 90f)

        assertThat(beyond.y).isWithin(TOLERANCE).of(atLimit.y)
        assertThat(beyond.x).isWithin(TOLERANCE).of(atLimit.x)
    }

    @Test
    fun `a rectangle outline is the four corners of the ratio box`() {
        val outline = PlanGeometry.outline(
            DeckPlan(shape = PlanShape.RECTANGLE, lengthRatio = 1f, breadthRatio = 0.5f),
        )

        assertThat(outline).hasSize(4)
        assertThat(outline.map { it.x }.distinct().sorted()).containsExactly(0.25f, 0.75f).inOrder()
        assertThat(outline.map { it.y }.distinct().sorted()).containsExactly(0f, 1f).inOrder()
    }

    @Test
    fun `a hull has a single point at the bow, and flips with bowAtTop`() {
        val plan = DeckPlan(shape = PlanShape.SHIP_HULL, lengthRatio = 1f, breadthRatio = 1f)

        val bowUp = PlanGeometry.outline(plan)
        val bowDown = PlanGeometry.outline(plan.copy(bowAtTop = false))

        assertThat(bowUp.minOf { it.y }).isWithin(TOLERANCE).of(0f)
        assertThat(bowUp.filter { it.y == bowUp.minOf { p -> p.y } }).hasSize(1)
        assertThat(bowDown.filter { it.y == bowDown.maxOf { p -> p.y } }).hasSize(1)
    }

    @Test
    fun `a custom polygon is drawn as authored, and a broken one falls back to the box`() {
        val triangle = listOf(PlanPoint(0.1f, 0.1f), PlanPoint(0.9f, 0.1f), PlanPoint(0.5f, 0.9f))

        assertThat(PlanGeometry.outline(DeckPlan(PlanShape.CUSTOM_POLYGON, polygon = triangle)))
            .isEqualTo(triangle)
        // Two points enclose nothing; a deck whose polygon was lost must still render.
        assertThat(PlanGeometry.outline(DeckPlan(PlanShape.CUSTOM_POLYGON, polygon = triangle.take(2))))
            .hasSize(4)
    }

    @Test
    fun `a point is inside a zone only when the ray cast says so`() {
        val square = listOf(
            PlanPoint(0.2f, 0.2f),
            PlanPoint(0.8f, 0.2f),
            PlanPoint(0.8f, 0.8f),
            PlanPoint(0.2f, 0.8f),
        )

        assertThat(PlanGeometry.contains(square, PlanPoint(0.5f, 0.5f))).isTrue()
        assertThat(PlanGeometry.contains(square, PlanPoint(0.1f, 0.5f))).isFalse()
        assertThat(PlanGeometry.contains(square, PlanPoint(0.5f, 0.9f))).isFalse()
    }

    @Test
    fun `a half-drawn zone contains nothing rather than claiming the whole deck`() {
        val line = listOf(PlanPoint(0.2f, 0.2f), PlanPoint(0.8f, 0.2f))

        assertThat(PlanGeometry.contains(line, PlanPoint(0.5f, 0.5f))).isFalse()
        assertThat(PlanGeometry.contains(emptyList(), PlanPoint(0.5f, 0.5f))).isFalse()
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
