package com.deckwatch.feature.deckview.geometry

import com.deckwatch.core.model.DeckPlan
import com.deckwatch.core.model.PlanPoint
import com.deckwatch.core.model.PlanShape
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** §6.3's plan shapes, as the polygons the canvas projects and hit-tests. */
class DeckOutlineGeometryTest {

    @Test
    fun `a full-size rectangle is the unit square`() {
        val polygon = DeckOutlineGeometry.polygon(DeckPlan(shape = PlanShape.RECTANGLE))

        assertThat(polygon).containsExactly(
            Vec2(0f, 0f),
            Vec2(1f, 0f),
            Vec2(1f, 1f),
            Vec2(0f, 1f),
        ).inOrder()
    }

    @Test
    fun `length and breadth ratios inset the outline about the plan centre`() {
        val polygon = DeckOutlineGeometry.polygon(
            DeckPlan(shape = PlanShape.RECTANGLE, lengthRatio = 0.5f, breadthRatio = 0.5f),
        )

        assertThat(polygon).containsExactly(
            Vec2(0.25f, 0.25f),
            Vec2(0.75f, 0.25f),
            Vec2(0.75f, 0.75f),
            Vec2(0.25f, 0.75f),
        ).inOrder()
    }

    @Test
    fun `the ship hull contains its parallel body and excludes the tapered bow corner`() {
        val hull = DeckOutlineGeometry.polygon(DeckPlan(shape = PlanShape.SHIP_HULL))

        assertThat(Polygons.contains(hull, Vec2(0.5f, 0.5f))).isTrue()
        assertThat(Polygons.contains(hull, Vec2(0.10f, 0.60f))).isTrue()
        // Forward of the shoulder the hull has narrowed; the port bow corner is water, not deck.
        assertThat(Polygons.contains(hull, Vec2(0.10f, 0.05f))).isFalse()
        assertThat(Polygons.contains(hull, Vec2(1.4f, 0.5f))).isFalse()
    }

    @Test
    fun `a sharper bow narrows the forward end`() {
        val blunt = DeckOutlineGeometry.polygon(
            DeckPlan(shape = PlanShape.SHIP_HULL, bowSharpness = 0f),
        )
        val fine = DeckOutlineGeometry.polygon(
            DeckPlan(shape = PlanShape.SHIP_HULL, bowSharpness = 1f),
        )

        val forwardPort = Vec2(0.15f, 0.02f)
        assertThat(Polygons.contains(blunt, forwardPort)).isTrue()
        assertThat(Polygons.contains(fine, forwardPort)).isFalse()
    }

    @Test
    fun `bowAtTop false mirrors the hull about the plan centre line`() {
        val mirrored = DeckOutlineGeometry.polygon(
            DeckPlan(shape = PlanShape.SHIP_HULL, bowAtTop = false),
        )

        assertThat(Polygons.contains(mirrored, Vec2(0.10f, 0.95f))).isFalse()
        assertThat(Polygons.contains(mirrored, Vec2(0.10f, 0.40f))).isTrue()
    }

    @Test
    fun `the L shape leaves its notch outside`() {
        val shape = DeckOutlineGeometry.polygon(DeckPlan(shape = PlanShape.L_SHAPE))

        assertThat(Polygons.contains(shape, Vec2(0.30f, 0.30f))).isTrue()
        assertThat(Polygons.contains(shape, Vec2(0.90f, 0.80f))).isTrue()
        assertThat(Polygons.contains(shape, Vec2(0.90f, 0.10f))).isFalse()
    }

    @Test
    fun `a custom polygon is used as given`() {
        val plan = DeckPlan(
            shape = PlanShape.CUSTOM_POLYGON,
            polygon = listOf(PlanPoint(0.2f, 0.2f), PlanPoint(0.8f, 0.2f), PlanPoint(0.5f, 0.9f)),
        )

        val shape = DeckOutlineGeometry.polygon(plan)

        assertThat(shape).hasSize(3)
        assertThat(Polygons.contains(shape, Vec2(0.5f, 0.4f))).isTrue()
        assertThat(Polygons.contains(shape, Vec2(0.25f, 0.8f))).isFalse()
    }

    @Test
    fun `a half-drawn custom polygon falls back to the rectangle so the deck stays tappable`() {
        val plan = DeckPlan(
            shape = PlanShape.CUSTOM_POLYGON,
            polygon = listOf(PlanPoint(0.2f, 0.2f), PlanPoint(0.8f, 0.2f)),
        )

        assertThat(DeckOutlineGeometry.polygon(plan)).hasSize(4)
    }

    @Test
    fun `planHash separates plans that draw differently and matches ones that do not`() {
        val bulker = DeckPlan(shape = PlanShape.SHIP_HULL, bowSharpness = 0.4f)
        val sameBulker = DeckPlan(shape = PlanShape.SHIP_HULL, bowSharpness = 0.4f)
        val finer = DeckPlan(shape = PlanShape.SHIP_HULL, bowSharpness = 0.9f)

        assertThat(DeckOutlineGeometry.planHash(bulker))
            .isEqualTo(DeckOutlineGeometry.planHash(sameBulker))
        assertThat(DeckOutlineGeometry.planHash(bulker))
            .isNotEqualTo(DeckOutlineGeometry.planHash(finer))
    }

    @Test
    fun `point in polygon rejects degenerate outlines`() {
        assertThat(Polygons.contains(emptyList(), Vec2(0f, 0f))).isFalse()
        assertThat(Polygons.contains(listOf(Vec2(0f, 0f), Vec2(1f, 1f)), Vec2(0.5f, 0.5f))).isFalse()
    }

    @Test
    fun `centroid of a zone polygon is its arithmetic centre`() {
        val zone = Polygons.of(
            listOf(
                PlanPoint(0f, 0f),
                PlanPoint(1f, 0f),
                PlanPoint(1f, 1f),
                PlanPoint(0f, 1f),
            ),
        )

        val centre = Polygons.centroid(zone)

        assertThat(centre.x).isWithin(1e-4f).of(0.5f)
        assertThat(centre.y).isWithin(1e-4f).of(0.5f)
    }
}
