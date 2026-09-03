package com.deckwatch.feature.deckview.geometry

import com.google.common.truth.Truth.assertThat
import kotlin.math.hypot
import org.junit.Test

/**
 * The compass turn of §7.2.
 *
 * The projection gains one term for it, so these tests pin the three things that term has to keep
 * true: that no yaw changes nothing, that a turn actually moves the ship round its own centre
 * rather than across the screen, and that [IsoProjection.unproject] still lands a dropped marker
 * where the finger was.
 */
class IsoProjectionYawTest {

    @Test
    fun `no yaw projects exactly as before`() {
        val plain = IsoProjection(angleDeg = 30f, scale = 200f)
        val levelled = IsoProjection(angleDeg = 30f, scale = 200f, yawDeg = 0f)

        val a = plain.project(0.8f, 0.2f)
        val b = levelled.project(0.8f, 0.2f)

        assertThat(b.x).isWithin(TOLERANCE).of(a.x)
        assertThat(b.y).isWithin(TOLERANCE).of(a.y)
    }

    @Test
    fun `the plan centre is the pivot, so turning never walks the ship off screen`() {
        for (yaw in listOf(0f, 45f, 90f, 213f, 359f)) {
            val projection = IsoProjection(angleDeg = 30f, scale = 200f, yawDeg = yaw)
            val centre = projection.project(CENTRE, CENTRE)

            assertThat(centre.x).isWithin(TOLERANCE).of(0f)
            assertThat(centre.y).isWithin(TOLERANCE).of(0f)
        }
    }

    @Test
    fun `a flat plan turned through a right angle sends the bow to where starboard was`() {
        // At angle 0 the projection is the identity, so the turn is readable without the squash.
        val levelled = IsoProjection(angleDeg = 0f, scale = 200f)
        val turned = IsoProjection(angleDeg = 0f, scale = 200f, yawDeg = 90f)

        // Bow-up plans put the bow at the top, which is the smaller y.
        val bow = turned.project(CENTRE, CENTRE - HALF_LENGTH)
        val starboardBeam = levelled.project(CENTRE + HALF_LENGTH, CENTRE)

        assertThat(bow.x).isWithin(TOLERANCE).of(starboardBeam.x)
        assertThat(bow.y).isWithin(TOLERANCE).of(starboardBeam.y)
    }

    @Test
    fun `turning preserves distance from the centre`() {
        val levelled = IsoProjection(angleDeg = 0f, scale = 200f)
        val turned = IsoProjection(angleDeg = 0f, scale = 200f, yawDeg = 37f)

        val before = levelled.project(0.9f, 0.3f)
        val after = turned.project(0.9f, 0.3f)

        assertThat(hypot(after.x, after.y)).isWithin(TOLERANCE).of(hypot(before.x, before.y))
    }

    @Test
    fun `unproject inverts project at any yaw, so a drop lands under the finger`() {
        for (yaw in listOf(0f, 30f, 90f, 180f, 275f)) {
            val projection = IsoProjection(
                angleDeg = 30f,
                scale = 340f,
                deckHeightPx = 64f,
                spread = 1.5f,
                yawDeg = yaw,
            )
            val screen = projection.project(0.7f, 0.35f, levelZ = 3)
            val plan = projection.unproject(screen, levelZ = 3)

            assertThat(plan.x).isWithin(TOLERANCE).of(0.7f)
            assertThat(plan.y).isWithin(TOLERANCE).of(0.35f)
        }
    }

    @Test
    fun `a full turn is the same view as none`() {
        val levelled = IsoProjection(angleDeg = 30f, scale = 200f, yawDeg = 0f)
        val roundAgain = IsoProjection(angleDeg = 30f, scale = 200f, yawDeg = 360f)

        val a = levelled.project(0.8f, 0.2f)
        val b = roundAgain.project(0.8f, 0.2f)

        assertThat(b.x).isWithin(TOLERANCE).of(a.x)
        assertThat(b.y).isWithin(TOLERANCE).of(a.y)
    }

    @Test
    fun `deck separation is vertical whichever way the ship faces`() {
        val turned = IsoProjection(angleDeg = 30f, scale = 200f, deckHeightPx = 64f, yawDeg = 120f)

        val lower = turned.project(0.6f, 0.4f, levelZ = 0)
        val upper = turned.project(0.6f, 0.4f, levelZ = 1)

        assertThat(upper.x).isWithin(TOLERANCE).of(lower.x)
        assertThat(lower.y - upper.y).isWithin(TOLERANCE).of(turned.levelStepPx)
    }

    @Test
    fun `yaw wraps into a single turn`() {
        assertThat(IsoProjection.normaliseYaw(0f)).isWithin(TOLERANCE).of(0f)
        assertThat(IsoProjection.normaliseYaw(370f)).isWithin(TOLERANCE).of(10f)
        assertThat(IsoProjection.normaliseYaw(-10f)).isWithin(TOLERANCE).of(350f)
        assertThat(IsoProjection.normaliseYaw(-730f)).isWithin(TOLERANCE).of(350f)
    }

    @Test
    fun `the bearing runs opposite the turn, because the ship moves and the viewer does not`() {
        assertThat(IsoProjection.bearingFor(0f)).isWithin(TOLERANCE).of(0f)
        // Turn the ship 90° clockwise and the part that lay to port is now at the top.
        assertThat(IsoProjection.bearingFor(90f)).isWithin(TOLERANCE).of(270f)
        assertThat(IsoProjection.bearingFor(-90f)).isWithin(TOLERANCE).of(90f)
    }

    @Test
    fun `the tape takes the short way round through the bow`() {
        assertThat(IsoProjection.signedDelta(target = 10f, bearing = 0f)).isWithin(TOLERANCE).of(10f)
        // 350 is ten degrees to port of the bow, not 350 degrees the long way round.
        assertThat(IsoProjection.signedDelta(target = 350f, bearing = 0f)).isWithin(TOLERANCE).of(-10f)
        assertThat(IsoProjection.signedDelta(target = 10f, bearing = 350f)).isWithin(TOLERANCE).of(20f)
    }

    private companion object {
        const val TOLERANCE = 0.001f
        const val CENTRE = 0.5f
        const val HALF_LENGTH = 0.3f
    }
}
