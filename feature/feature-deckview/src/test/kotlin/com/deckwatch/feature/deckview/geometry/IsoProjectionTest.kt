package com.deckwatch.feature.deckview.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** §7.2's projection: flat at 0°, dimetric at the top of the range, and invertible throughout. */
class IsoProjectionTest {

    @Test
    fun `angle zero collapses to the flat plan`() {
        val projection = IsoProjection(angleDeg = 0f, scale = 200f, deckHeightPx = 64f)

        val topLeft = projection.project(0f, 0f)
        val bottomRight = projection.project(1f, 1f)
        val centre = projection.project(0.5f, 0.5f)

        // The plan is projected about its centre, so (0.5, 0.5) is the origin and the outline is an
        // axis-aligned square of `scale` pixels — a true top-down plan, not a parallelogram.
        assertThat(centre.x).isWithin(TOLERANCE).of(0f)
        assertThat(centre.y).isWithin(TOLERANCE).of(0f)
        assertThat(topLeft.x).isWithin(TOLERANCE).of(-100f)
        assertThat(topLeft.y).isWithin(TOLERANCE).of(-100f)
        assertThat(bottomRight.x).isWithin(TOLERANCE).of(100f)
        assertThat(bottomRight.y).isWithin(TOLERANCE).of(100f)
    }

    @Test
    fun `angle zero keeps the plan axes square`() {
        val projection = IsoProjection(angleDeg = 0f, scale = 200f)

        val alongX = projection.project(1f, 0.5f)
        val alongY = projection.project(0.5f, 1f)

        assertThat(alongX.y).isWithin(TOLERANCE).of(0f)
        assertThat(alongY.x).isWithin(TOLERANCE).of(0f)
    }

    @Test
    fun `a positive angle skews the plan into a parallelogram`() {
        val projection = IsoProjection(angleDeg = 30f, scale = 200f)

        val alongX = projection.project(1f, 0.5f)
        val alongY = projection.project(0.5f, 1f)

        // Both plan axes now carry a vertical component, and they lean opposite ways.
        assertThat(alongX.y).isGreaterThan(0f)
        assertThat(alongY.y).isGreaterThan(0f)
        assertThat(alongX.x).isGreaterThan(0f)
        assertThat(alongY.x).isLessThan(0f)
    }

    @Test
    fun `levelZ lifts a deck by one deck height per rank, scaled by spread`() {
        val projection = IsoProjection(angleDeg = 30f, scale = 200f, deckHeightPx = 64f, spread = 2f)

        val ground = projection.project(0.5f, 0.5f, levelZ = 0)
        val second = projection.project(0.5f, 0.5f, levelZ = 1)
        val fourth = projection.project(0.5f, 0.5f, levelZ = 3)

        assertThat(second.y).isWithin(TOLERANCE).of(ground.y - 128f)
        assertThat(fourth.y).isWithin(TOLERANCE).of(ground.y - 384f)
        assertThat(second.x).isWithin(TOLERANCE).of(ground.x)
    }

    @Test
    fun `deck height is a screen constant and is not multiplied by the plan scale`() {
        val small = IsoProjection(angleDeg = 30f, scale = 100f, deckHeightPx = 64f)
        val large = IsoProjection(angleDeg = 30f, scale = 900f, deckHeightPx = 64f)

        assertThat(small.levelStepPx).isWithin(TOLERANCE).of(large.levelStepPx)
    }

    @Test
    fun `unproject inverts project at every angle`() {
        for (angle in listOf(0f, 12f, 30f, 35f)) {
            val projection = IsoProjection(angleDeg = angle, scale = 340f, deckHeightPx = 64f, spread = 1.5f)
            for (point in listOf(Vec2(0f, 0f), Vec2(0.25f, 0.8f), Vec2(1f, 1f))) {
                val screen = projection.project(point.x, point.y, levelZ = 2)
                val back = projection.unproject(screen.x, screen.y, levelZ = 2)
                assertThat(back.x).isWithin(TOLERANCE).of(point.x)
                assertThat(back.y).isWithin(TOLERANCE).of(point.y)
            }
        }
    }

    @Test
    fun `at the top of the range the projection is the section 7 2 formula up to one uniform scale`() {
        val projection = IsoProjection(angleDeg = IsoProjection.MAX_ANGLE_DEG, scale = 200f)
        val points = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(0.2f, 0.9f))

        val ratios = points.flatMap { point ->
            val ours = projection.project(point.x, point.y)
            val spec = projection.projectSpec(point.x, point.y)
            buildList {
                if (spec.x != 0f) add(ours.x / spec.x)
                if (spec.y != 0f) add(ours.y / spec.y)
            }
        }

        assertThat(ratios).isNotEmpty()
        val first = ratios.first()
        ratios.forEach { assertThat(it).isWithin(TOLERANCE).of(first) }
    }

    @Test
    fun `angle and spread are clamped to the section 7 2 ranges`() {
        assertThat(IsoProjection.clampAngle(-4f)).isEqualTo(0f)
        assertThat(IsoProjection.clampAngle(90f)).isEqualTo(IsoProjection.MAX_ANGLE_DEG)
        assertThat(IsoProjection.clampSpread(0.1f)).isEqualTo(IsoProjection.MIN_SPREAD)
        assertThat(IsoProjection.clampSpread(9f)).isEqualTo(IsoProjection.MAX_SPREAD)
        assertThat(IsoProjection(angleDeg = 120f).angle).isEqualTo(IsoProjection.MAX_ANGLE_DEG)
    }

    @Test
    fun `squash runs from one at a flat plan to tan theta at the top of the range`() {
        assertThat(IsoProjection(angleDeg = 0f).squash).isWithin(TOLERANCE).of(1f)
        val top = IsoProjection(angleDeg = IsoProjection.MAX_ANGLE_DEG)
        assertThat(top.squash).isWithin(TOLERANCE).of(
            kotlin.math.tan(IsoProjection.MAX_ANGLE_DEG * IsoProjection.DEG_TO_RAD),
        )
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
