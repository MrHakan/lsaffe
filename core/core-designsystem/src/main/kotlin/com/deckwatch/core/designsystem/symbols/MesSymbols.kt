package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

/**
 * `MES` — escape and muster pictograms (green ground), drawn from scratch.
 * The `APP_*` members of this series are the markers the standards leave to
 * the shipboard plan (arrows, low-location lighting, escape trunks).
 */
object MesSymbols {

    /** MES001 — shipboard assembly / muster station. */
    val MES001: ImageVector by lazy {
        symbolVector("MES001") {
            solid {
                person(6.2f, 8.6f, 0.95f)
                person(12f, 7.0f, 1.05f)
                person(17.8f, 8.6f, 0.95f)
                bar(2.6f, 20.6f, 21.4f, 20.6f, 1.8f)
            }
        }
    }

    /** MES002 — emergency exit to the left. */
    val MES002: ImageVector by lazy {
        symbolVector("MES002") {
            cutout { exitDoor(mirror = true) }
            solid { exitFigure(mirror = true) }
        }
    }

    /** MES003 — emergency exit to the right. */
    val MES003: ImageVector by lazy {
        symbolVector("MES003") {
            cutout { exitDoor(mirror = false) }
            solid { exitFigure(mirror = false) }
        }
    }

    /** APP_ARROW — directional arrow; the renderer rotates it in 90° steps. */
    val ARROW: ImageVector by lazy {
        symbolVector("APP_ARROW") {
            solid {
                bar(3.0f, 12f, 15.0f, 12f, 3.4f)
                triangle(14.0f, 5.4f, 21.4f, 12f, 14.0f, 18.6f)
            }
        }
    }

    /** APP_LLL — low-location lighting: floor-level marking strip. */
    val LLL: ImageVector by lazy {
        symbolVector("APP_LLL") {
            solid {
                rect(2.2f, 19.0f, 21.8f, 21.0f)
                roundRect(3.0f, 15.4f, 6.6f, 17.8f, 0.6f)
                roundRect(8.0f, 15.4f, 11.6f, 17.8f, 0.6f)
                roundRect(13.0f, 15.4f, 16.6f, 17.8f, 0.6f)
                roundRect(18.0f, 15.4f, 21.0f, 17.8f, 0.6f)
                bar(6.0f, 13.6f, 6.0f, 11.4f, 1.2f)
                bar(12f, 13.0f, 12f, 10.2f, 1.2f)
                bar(18.0f, 13.6f, 18.0f, 11.4f, 1.2f)
            }
        }
    }

    /** APP_ESCAPE_TRUNK — emergency escape trunk with its ladder. */
    val ESCAPE_TRUNK: ImageVector by lazy {
        symbolVector("APP_ESCAPE_TRUNK") {
            cutout {
                roundRect(5.6f, 2.6f, 18.4f, 21.4f, 1.6f)
                roundRect(7.4f, 4.4f, 16.6f, 21.4f, 0.8f)
            }
            solid {
                triangle(9.6f, 7.0f, 12f, 4.8f, 14.4f, 7.0f)
                bar(7.4f, 10.0f, 16.6f, 10.0f, 1.5f)
                bar(7.4f, 13.6f, 16.6f, 13.6f, 1.5f)
                bar(7.4f, 17.2f, 16.6f, 17.2f, 1.5f)
                bar(7.4f, 20.8f, 16.6f, 20.8f, 1.5f)
            }
        }
    }
}

/** Mirrors an x-coordinate about the centre line when [mirror] is set. */
private fun mx(mirror: Boolean, x: Float): Float = if (mirror) SYMBOL_VIEWPORT - x else x

/** Mirror-safe rectangle: the sides are re-ordered so the winding stays clockwise. */
private fun PathBuilder.rectM(
    mirror: Boolean,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val a = mx(mirror, left)
    val b = mx(mirror, right)
    rect(minOf(a, b), top, maxOf(a, b), bottom)
}

/** The doorway of the emergency-exit signs, as a frame open on the exit side. */
private fun PathBuilder.exitDoor(mirror: Boolean) {
    rectM(mirror, 14.0f, 3.4f, 21.4f, 20.6f)
    rectM(mirror, 15.8f, 5.2f, 21.4f, 18.8f)
}

/** The figure making for the door: head, body, stride and leading arm. */
private fun PathBuilder.exitFigure(mirror: Boolean) {
    circle(mx(mirror, 8.0f), 5.6f, 2.1f)
    rectM(mirror, 6.1f, 8.2f, 10.3f, 14.0f)
    bar(mx(mirror, 7.6f), 13.4f, mx(mirror, 5.4f), 19.8f, 2.1f)
    bar(mx(mirror, 9.6f), 13.4f, mx(mirror, 12.0f), 19.8f, 2.1f)
    bar(mx(mirror, 10.0f), 9.2f, mx(mirror, 12.8f), 11.8f, 1.9f)
}
