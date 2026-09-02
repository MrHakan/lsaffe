package com.deckwatch.feature.report

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The exported plan geometry — §6.3 mirrored into SVG, §13.4. */
class DeckSvgTest {

    private val width = 340f
    private val height = 440f

    @Test
    fun `a rectangle plan is four lines and a close`() {
        val path = DeckSvg.outlinePath(DeckPlan(shape = PlanShape.RECTANGLE), width, height)
        assertThat(path).isEqualTo("M 0 0 L 340 0 L 340 440 L 0 440 Z")
    }

    @Test
    fun `length and breadth ratios inset the outline in the plan box`() {
        val plan = DeckPlan(shape = PlanShape.RECTANGLE, lengthRatio = 0.5f, breadthRatio = 0.5f)
        val path = DeckSvg.outlinePath(plan, width, height)
        assertThat(path).isEqualTo("M 85 110 L 255 110 L 255 330 L 85 330 Z")
    }

    @Test
    fun `a ship hull tapers at the bow and rounds at the stern`() {
        val path = DeckSvg.outlinePath(DeckPlan(shape = PlanShape.SHIP_HULL), width, height)
        assertThat(path).startsWith("M ")
        assertThat(path).contains("Q ")
        assertThat(path).endsWith(" Z")
    }

    @Test
    fun `bowAtTop false mirrors the hull about the plan box centre line`() {
        val up = DeckSvg.outlinePath(DeckPlan(shape = PlanShape.SHIP_HULL, bowAtTop = true), width, height)
        val down = DeckSvg.outlinePath(DeckPlan(shape = PlanShape.SHIP_HULL, bowAtTop = false), width, height)
        assertThat(down).isNotEqualTo(up)

        // Every y in the mirrored path is the reflection of the corresponding y in the original.
        val upY = yValues(up)
        val downY = yValues(down)
        assertThat(downY).hasSize(upY.size)
        upY.zip(downY).forEach { (a, b) -> assertThat(a + b).isWithin(TOLERANCE).of(height) }
    }

    @Test
    fun `a custom polygon with too few points falls back to the box`() {
        val degenerate = DeckPlan(
            shape = PlanShape.CUSTOM_POLYGON,
            polygon = listOf(PlanPoint(0f, 0f), PlanPoint(1f, 1f)),
        )
        assertThat(DeckSvg.outlinePath(degenerate, width, height))
            .isEqualTo(DeckSvg.outlinePath(DeckPlan(shape = PlanShape.RECTANGLE), width, height))
    }

    @Test
    fun `a custom polygon is scaled into the viewBox`() {
        val plan = DeckPlan(
            shape = PlanShape.CUSTOM_POLYGON,
            polygon = listOf(PlanPoint(0f, 0f), PlanPoint(1f, 0f), PlanPoint(0.5f, 1f)),
        )
        assertThat(DeckSvg.outlinePath(plan, width, height)).isEqualTo("M 0 0 L 340 0 L 170 440 Z")
    }

    @Test
    fun `an L-shape has the notch of section 6_3`() {
        val path = DeckSvg.outlinePath(DeckPlan(shape = PlanShape.L_SHAPE), width, height)
        assertThat(path.count { it == 'L' }).isEqualTo(5)
    }

    @Test
    fun `marker coordinates are clamped into the plan box`() {
        assertThat(DeckSvg.markerPoint(0.5f, 0.25f, width, height)).isEqualTo(170f to 110f)
        assertThat(DeckSvg.markerPoint(-3f, 9f, width, height)).isEqualTo(0f to height)
    }

    @Test
    fun `numbers are locale-neutral and trimmed`() {
        assertThat(DeckSvg.num(12f)).isEqualTo("12")
        assertThat(DeckSvg.num(12.5f)).isEqualTo("12.5")
        assertThat(DeckSvg.num(12.345f)).isEqualTo("12.35")
        assertThat(DeckSvg.num(0f)).isEqualTo("0")
    }

    /**
     * The exported plan must keep matching the app's canvas. These constants are copied from
     * `feature-vessel/.../common/DeckOutline.kt`; if that file changes, this test is the thing that
     * notices.
     */
    @Test
    fun `the geometry constants match the app's deck outline`() {
        assertThat(DeckSvg.MIN_RATIO).isEqualTo(0.05f)
        assertThat(DeckSvg.MIN_POLYGON_POINTS).isEqualTo(3)
        assertThat(DeckSvg.L_NOTCH_FRACTION).isEqualTo(0.55f)
        assertThat(DeckSvg.BOW_MIN_FRACTION).isEqualTo(0.10f)
        assertThat(DeckSvg.BOW_MAX_FRACTION).isEqualTo(0.34f)
        assertThat(DeckSvg.BOW_TIP_PINCH).isEqualTo(0.92f)
        assertThat(DeckSvg.BOW_CROWN).isEqualTo(0.22f)
        assertThat(DeckSvg.SHOULDER_PULL).isEqualTo(0.45f)
    }

    /** Every second number in a path built only from M / L / Q commands is a y coordinate. */
    private fun yValues(path: String): List<Float> =
        path.split(' ')
            .mapNotNull { it.toFloatOrNull() }
            .filterIndexed { index, _ -> index % 2 == 1 }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
