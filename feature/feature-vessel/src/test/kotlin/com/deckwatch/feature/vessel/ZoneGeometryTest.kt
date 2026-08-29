package com.deckwatch.feature.vessel

import com.deckwatch.core.model.PlanPoint
import com.deckwatch.feature.vessel.zone.ZoneGeometry
import com.deckwatch.feature.vessel.zone.ZoneRect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The list-mode zone editor's rectangle ↔ polygon mapping — see [ZoneGeometry] for the rationale. */
class ZoneGeometryTest {

    @Test
    fun `a rectangle becomes four points clockwise from the top-left`() {
        val polygon = ZoneGeometry.rectToPolygon(ZoneRect(0.2f, 0.1f, 0.8f, 0.5f))

        assertThat(polygon).containsExactly(
            PlanPoint(0.2f, 0.1f),
            PlanPoint(0.8f, 0.1f),
            PlanPoint(0.8f, 0.5f),
            PlanPoint(0.2f, 0.5f),
        ).inOrder()
    }

    @Test
    fun `crossed edges are ordered rather than refused`() {
        val polygon = ZoneGeometry.rectToPolygon(ZoneRect(left = 0.8f, top = 0.6f, right = 0.2f, bottom = 0.1f))

        assertThat(polygon.minOf { it.x }).isEqualTo(0.2f)
        assertThat(polygon.maxOf { it.x }).isEqualTo(0.8f)
        assertThat(polygon.minOf { it.y }).isEqualTo(0.1f)
        assertThat(polygon.maxOf { it.y }).isEqualTo(0.6f)
        assertThat(polygon.first()).isEqualTo(PlanPoint(0.2f, 0.1f))
    }

    @Test
    fun `edges are clamped into the unit plan space`() {
        val rect = ZoneGeometry.normalise(ZoneRect(left = -3f, top = -1f, right = 9f, bottom = 4f))

        assertThat(rect.left).isAtLeast(0f)
        assertThat(rect.top).isAtLeast(0f)
        assertThat(rect.right).isAtMost(1f)
        assertThat(rect.bottom).isAtMost(1f)
    }

    @Test
    fun `a collapsed rectangle is widened to the minimum size`() {
        val rect = ZoneGeometry.normalise(ZoneRect(0.5f, 0.5f, 0.5f, 0.5f))

        assertThat(rect.right - rect.left).isWithin(FLOAT_TOLERANCE).of(ZoneGeometry.MIN_SIZE)
        assertThat(rect.bottom - rect.top).isWithin(FLOAT_TOLERANCE).of(ZoneGeometry.MIN_SIZE)
    }

    @Test
    fun `a rectangle round-trips through the polygon unchanged`() {
        val original = ZoneRect(0.15f, 0.25f, 0.65f, 0.9f)

        val roundTripped = ZoneGeometry.polygonToRect(ZoneGeometry.rectToPolygon(original))

        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun `an arbitrary polygon degrades to its bounding box`() {
        val triangle = listOf(PlanPoint(0.5f, 0.1f), PlanPoint(0.9f, 0.7f), PlanPoint(0.2f, 0.6f))

        val rect = ZoneGeometry.polygonToRect(triangle)

        assertThat(rect).isEqualTo(ZoneRect(left = 0.2f, top = 0.1f, right = 0.9f, bottom = 0.7f))
    }

    @Test
    fun `an empty polygon falls back to the default rectangle`() {
        assertThat(ZoneGeometry.polygonToRect(emptyList())).isEqualTo(ZoneGeometry.Default)
    }

    private companion object {
        /** Float rounding: 0.52f - 0.5f is 0.01999998, not exactly [ZoneGeometry.MIN_SIZE]. */
        const val FLOAT_TOLERANCE = 1e-4f
    }
}
