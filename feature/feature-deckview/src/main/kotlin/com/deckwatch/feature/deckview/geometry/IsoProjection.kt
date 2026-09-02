package com.deckwatch.feature.deckview.geometry

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** A point in plan space (0..1) or in projected screen space (pixels, relative to the stack origin). */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(factor: Float): Vec2 = Vec2(x * factor, y * factor)

    companion object {
        val Zero = Vec2(0f, 0f)
    }
}

/**
 * The 2.5D projection of §7.2 — pure maths, no Compose, so the renderer's geometry is unit-testable
 * on the JVM.
 *
 * §7.2 states the projection as
 *
 * ```
 * screenX = (planX - planY) * cos(θ) * scale
 * screenY = (planX + planY) * sin(θ) * scale - levelZ * deckHeight * scale
 * ```
 *
 * and *also* requires that `θ = 0` collapse to the flat top-down plan, so that mode B's flat/iso
 * toggle is a single animated float rather than a second renderer. Those two statements cannot both
 * hold literally: at `θ = 0` the formula above sends every point onto one horizontal line, because
 * `sin 0 = 0`.
 *
 * The formula is the *rotate-45°-then-squash-vertically* construction written out: it rotates the
 * plan by 45° and scales the result by `√2·cos θ` horizontally and `√2·sin θ` vertically — i.e. a
 * uniform scale and a vertical squash of `tan θ`. This class implements exactly that construction
 * with both parameters driven off the angle so the flat plan is the natural `θ = 0` endpoint:
 *
 * * the in-plane rotation is `45° · u`, and
 * * the vertical squash is `lerp(1, tan θ, u)`,
 *
 * where `u = θ / 35°` runs over the §7.2 angle range. At `θ = 0` that is the identity — a true flat
 * plan. At the top of the range it is the §7.2 projection exactly, up to one uniform scale factor
 * (`cos 45° / cos 35°`); [projectSpec] renders the literal formula for comparison and the unit tests
 * pin the two together.
 *
 * Plan coordinates are the normalised 0..1 space of §6.3 and are projected about the plan's centre,
 * so a deck's projected origin is its own centre — the canvas then places that origin.
 *
 * @param angleDeg the user's `isoAngle` setting, clamped to 0°..35°.
 * @param scale screen pixels per unit of plan space — the deck's on-screen size at zoom 1.
 * @param deckHeightPx the constant screen-space deck separation of §7.2 (64dp at scale 1).
 * @param spread the §7.2 fan slider, 0.5×–3×.
 */
data class IsoProjection(
    val angleDeg: Float = DEFAULT_ANGLE_DEG,
    val scale: Float = 1f,
    val deckHeightPx: Float = DEFAULT_DECK_HEIGHT_PX,
    val spread: Float = 1f,
) {
    /** The angle actually used, clamped to the §7.2 range. */
    val angle: Float = angleDeg.coerceIn(MIN_ANGLE_DEG, MAX_ANGLE_DEG)

    /** How far along the flat → isometric range this projection sits, 0..1. */
    val tilt: Float = if (MAX_ANGLE_DEG == 0f) 0f else angle / MAX_ANGLE_DEG

    private val rotationRad: Float = HALF_RIGHT_ANGLE_RAD * tilt
    private val cosRho: Float = cos(rotationRad)
    private val sinRho: Float = sin(rotationRad)

    /** Vertical squash: 1 at a flat plan, `tan θ` at the top of the angle range. */
    val squash: Float = 1f + tilt * (tan(angle * DEG_TO_RAD) - 1f)

    /**
     * Screen-space separation of two adjacent decks.
     *
     * §7.2 makes `deckHeight` "a constant screen-space value (default 64dp at scale 1.0), not a
     * physical value", so it is *not* multiplied by [scale] — [scale] is pixels per unit of plan
     * space, which is a much larger number. The caller folds the zoom into both.
     */
    val levelStepPx: Float = deckHeightPx * spread.coerceIn(MIN_SPREAD, MAX_SPREAD)

    /**
     * Projects a plan point on the deck at rank [levelZ] into screen space, relative to the deck
     * stack's origin. `levelZ` is the deck's *rank* in the stack (see [DeckStackOrder]), never the
     * raw `levelIndex` — gaps of 10 must not fan the stack apart.
     */
    fun project(planX: Float, planY: Float, levelZ: Int = 0): Vec2 {
        val px = (planX - PLAN_CENTRE) * scale
        val py = (planY - PLAN_CENTRE) * scale
        val x = px * cosRho - py * sinRho
        val y = (px * sinRho + py * cosRho) * squash - levelZ * levelStepPx
        return Vec2(x, y)
    }

    /** [project] for a plan-space point. */
    fun project(point: Vec2, levelZ: Int = 0): Vec2 = project(point.x, point.y, levelZ)

    /**
     * The inverse of [project]: turns a screen-space point back into plan coordinates on the deck at
     * rank [levelZ]. The result is *not* clamped to 0..1 — a drop outside the outline has to be
     * detectable (§7.2 "returns to its origin with a shake").
     */
    fun unproject(screenX: Float, screenY: Float, levelZ: Int = 0): Vec2 {
        if (scale <= 0f || squash <= 0f) return Vec2(PLAN_CENTRE, PLAN_CENTRE)
        val x = screenX
        val y = (screenY + levelZ * levelStepPx) / squash
        val px = x * cosRho + y * sinRho
        val py = -x * sinRho + y * cosRho
        return Vec2(px / scale + PLAN_CENTRE, py / scale + PLAN_CENTRE)
    }

    /** [unproject] for a screen-space point. */
    fun unproject(point: Vec2, levelZ: Int = 0): Vec2 = unproject(point.x, point.y, levelZ)

    /**
     * The literal §7.2 formula, kept for reference and pinned to [project] by the unit tests at the
     * top of the angle range. Not used by the renderer, because it degenerates at `θ = 0`.
     */
    fun projectSpec(planX: Float, planY: Float, levelZ: Int = 0): Vec2 {
        val px = planX - PLAN_CENTRE
        val py = planY - PLAN_CENTRE
        val rad = angle * DEG_TO_RAD
        return Vec2(
            x = (px - py) * cos(rad) * scale,
            y = (px + py) * sin(rad) * scale - levelZ * levelStepPx,
        )
    }

    companion object {
        const val MIN_ANGLE_DEG: Float = 0f
        const val MAX_ANGLE_DEG: Float = 35f
        const val DEFAULT_ANGLE_DEG: Float = 30f

        /** 64dp at scale 1.0 on a mdpi baseline; the canvas passes the real px value. */
        const val DEFAULT_DECK_HEIGHT_PX: Float = 64f

        const val MIN_SPREAD: Float = 0.5f
        const val MAX_SPREAD: Float = 3f
        const val DEFAULT_SPREAD: Float = 1f

        internal const val PLAN_CENTRE: Float = 0.5f
        internal const val DEG_TO_RAD: Float = (Math.PI / 180.0).toFloat()
        internal const val HALF_RIGHT_ANGLE_RAD: Float = (Math.PI / 4.0).toFloat()

        /** Clamps a spread slider value to the §7.2 fan range. */
        fun clampSpread(spread: Float): Float = spread.coerceIn(MIN_SPREAD, MAX_SPREAD)

        /** Clamps an isometric angle to the §7.2 setting range. */
        fun clampAngle(angleDeg: Float): Float = angleDeg.coerceIn(MIN_ANGLE_DEG, MAX_ANGLE_DEG)
    }
}
