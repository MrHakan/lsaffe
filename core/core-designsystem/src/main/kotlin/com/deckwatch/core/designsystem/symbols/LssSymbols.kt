package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

/**
 * `LSS` — life-saving appliance pictograms, drawn from scratch (§10.2, §10.3).
 * White on a transparent ground; the tile supplies the green ISO 3864-1 ground.
 */
object LssSymbols {

    /** LSS001 — lifeboat: enclosed canopy over a hull. */
    val LSS001: ImageVector by lazy {
        symbolVector("LSS001") {
            solid {
                domeUp(12f, 14.6f, 6.4f)
                polygon(3.2f, 14.6f, 20.8f, 14.6f, 18.2f, 19.8f, 5.8f, 19.8f)
            }
        }
    }

    /** LSS002 — rescue boat: open hull, coxswain, outboard engine. */
    val LSS002: ImageVector by lazy {
        symbolVector("LSS002") {
            solid {
                polygon(2.8f, 15.2f, 20.4f, 15.2f, 17.8f, 19.8f, 5.6f, 19.8f)
                person(10.4f, 9.0f, 0.85f)
                roundRect(18.4f, 10.4f, 21.2f, 15.4f, 1.0f)
            }
        }
    }

    /** LSS003 — liferaft: canopy on an inflated tube, on the water. */
    val LSS003: ImageVector by lazy {
        symbolVector("LSS003") {
            solid {
                domeUp(12f, 13.6f, 6.2f)
                roundRect(3.6f, 13.0f, 20.4f, 16.8f, 1.9f)
                bar(3.0f, 19.8f, 21.0f, 19.8f, 1.6f)
            }
        }
    }

    /** LSS004 — davit-launched liferaft: davit arm, fall, raft. */
    val LSS004: ImageVector by lazy {
        symbolVector("LSS004") {
            solid {
                bar(4.2f, 3.0f, 4.2f, 11.6f, 1.9f)
                bar(3.6f, 3.6f, 13.4f, 3.6f, 1.9f)
                bar(13.4f, 3.6f, 13.4f, 10.6f, 1.1f)
                domeUp(13.4f, 15.6f, 5.2f)
                roundRect(6.6f, 15.0f, 20.4f, 18.2f, 1.6f)
            }
        }
    }

    /** LSS005 — lifebuoy: ring with four grab-line lashings. */
    val LSS005: ImageVector by lazy {
        symbolVector("LSS005") {
            cutout {
                circle(12f, 12f, 8.6f)
                circle(12f, 12f, 5.0f)
            }
            solid {
                rays(12f, 12f, 4.0f, 9.6f, 1.9f, 45f, 135f, 225f, 315f)
            }
        }
    }

    /** LSS006 — lifebuoy with buoyant line. */
    val LSS006: ImageVector by lazy {
        symbolVector("LSS006") {
            cutout {
                circle(9.2f, 12.4f, 7.2f)
                circle(9.2f, 12.4f, 4.1f)
            }
            solid {
                rays(9.2f, 12.4f, 3.3f, 8.0f, 1.6f, 45f, 135f, 225f, 315f)
                arcBand(18.4f, 15.6f, 2.6f, 90f, 180f, 1.4f)
                arcBand(18.4f, 10.4f, 2.6f, 270f, 180f, 1.4f)
            }
        }
    }

    /** LSS007 — lifebuoy with self-igniting light. */
    val LSS007: ImageVector by lazy {
        symbolVector("LSS007") {
            cutout {
                circle(10.4f, 13.6f, 7.2f)
                circle(10.4f, 13.6f, 4.1f)
            }
            solid {
                rays(10.4f, 13.6f, 3.3f, 8.0f, 1.6f, 45f, 135f, 225f, 315f)
                circle(19.0f, 5.4f, 2.1f)
                rays(19.0f, 5.4f, 3.1f, 4.9f, 1.2f, 270f, 315f, 200f)
            }
        }
    }

    /** LSS008 — lifebuoy with buoyant line and light. */
    val LSS008: ImageVector by lazy {
        symbolVector("LSS008") {
            cutout {
                circle(9.6f, 13.2f, 6.9f)
                circle(9.6f, 13.2f, 3.9f)
            }
            solid {
                rays(9.6f, 13.2f, 3.1f, 7.7f, 1.5f, 45f, 135f, 225f, 315f)
                circle(19.2f, 5.2f, 2.0f)
                rays(19.2f, 5.2f, 3.0f, 4.7f, 1.1f, 270f, 315f, 200f)
                arcBand(18.6f, 17.6f, 2.3f, 90f, 180f, 1.3f)
                arcBand(18.6f, 13.0f, 2.3f, 270f, 180f, 1.3f)
            }
        }
    }

    /** LSS008_1 — lifebuoy with light and self-activating smoke signal. */
    val LSS008_1: ImageVector by lazy {
        symbolVector("LSS008_1") {
            cutout {
                circle(9.8f, 14.4f, 6.8f)
                circle(9.8f, 14.4f, 3.9f)
            }
            solid {
                rays(9.8f, 14.4f, 3.1f, 7.6f, 1.5f, 45f, 135f, 225f, 315f)
                circle(19.0f, 17.4f, 1.9f)
                rays(19.0f, 17.4f, 2.9f, 4.4f, 1.1f, 0f, 45f, 315f)
                circle(15.6f, 6.4f, 2.6f)
                circle(19.4f, 4.6f, 2.0f)
                circle(19.8f, 8.4f, 1.7f)
            }
        }
    }

    /** LSS009 — lifejacket: adult vest. */
    val LSS009: ImageVector by lazy {
        symbolVector("LSS009") {
            solid { vest(12f, 5.6f, 15.0f) }
        }
    }

    /** LSS010 — child's lifejacket: the vest on a smaller wearer. */
    val LSS010: ImageVector by lazy {
        symbolVector("LSS010") {
            solid {
                circle(12f, 4.6f, 2.4f)
                vest(12f, 7.8f, 12.2f)
            }
        }
    }

    /** LSS011 — infant's lifejacket: smallest wearer, carried. */
    val LSS011: ImageVector by lazy {
        symbolVector("LSS011") {
            solid {
                circle(12f, 5.6f, 2.1f)
                vest(12f, 8.4f, 9.6f)
                bar(6.0f, 20.0f, 18.0f, 20.0f, 1.7f)
                bar(4.6f, 17.6f, 6.4f, 20.0f, 1.7f)
                bar(17.6f, 20.0f, 19.4f, 17.6f, 1.7f)
            }
        }
    }

    /** LSS012 — search and rescue transponder: set with radiated arcs. */
    val LSS012: ImageVector by lazy {
        symbolVector("LSS012") {
            cutout {
                roundRect(8.4f, 6.6f, 15.6f, 21.4f, 1.9f)
                rect(9.9f, 9.0f, 14.1f, 10.8f)
            }
            solid {
                roundRect(10.2f, 3.6f, 13.8f, 7.0f, 1.2f)
                arcBand(12f, 6.4f, 6.4f, 196f, 46f, 1.3f)
                arcBand(12f, 6.4f, 6.4f, 298f, 46f, 1.3f)
            }
        }
    }

    /** LSS013 — survival craft distress signals: pyrotechnics with a burst. */
    val LSS013: ImageVector by lazy {
        symbolVector("LSS013") {
            solid {
                bar(6.6f, 20.6f, 10.4f, 12.0f, 3.4f)
                bar(13.6f, 12.0f, 17.4f, 20.6f, 3.4f)
                circle(12f, 6.6f, 1.8f)
                rays(12f, 6.6f, 2.6f, 5.2f, 1.3f, 270f, 330f, 210f, 30f, 150f)
            }
        }
    }

    /** LSS014 — rocket parachute flare: canopy, shrouds, flare body. */
    val LSS014: ImageVector by lazy {
        symbolVector("LSS014") {
            solid {
                domeUp(12f, 9.0f, 6.3f)
                bar(6.4f, 9.2f, 12f, 14.4f, 1.1f)
                bar(12f, 9.2f, 12f, 14.4f, 1.1f)
                bar(12f, 14.4f, 17.6f, 9.2f, 1.1f)
                roundRect(10.3f, 14.0f, 13.7f, 20.8f, 1.3f)
            }
        }
    }

    /** LSS015 — line-throwing appliance: launcher, projectile, trailing line. */
    val LSS015: ImageVector by lazy {
        symbolVector("LSS015") {
            solid {
                roundRect(2.6f, 15.6f, 7.2f, 20.6f, 1.2f)
                arcBand(10f, 8.2f, 8.6f, 62f, 66f, 1.2f)
                bar(14.6f, 11.4f, 18.6f, 7.4f, 3.0f)
                triangle(17.5f, 6.3f, 21.0f, 4.9f, 19.9f, 8.5f)
            }
        }
    }

    /** LSS016 — two-way VHF radiotelephone for survival craft. */
    val LSS016: ImageVector by lazy {
        symbolVector("LSS016") {
            cutout {
                roundRect(8.2f, 5.8f, 15.8f, 21.4f, 1.9f)
                rect(9.7f, 7.8f, 14.3f, 10.6f)
                circle(10.4f, 13.6f, 1.0f)
                circle(13.6f, 13.6f, 1.0f)
                circle(10.4f, 16.6f, 1.0f)
                circle(13.6f, 16.6f, 1.0f)
            }
            solid {
                bar(15.0f, 6.2f, 18.6f, 2.6f, 1.5f)
            }
        }
    }

    /** LSS017 — EPIRB: tapered beacon with a strobe. */
    val LSS017: ImageVector by lazy {
        symbolVector("LSS017") {
            solid {
                polygon(9.0f, 8.8f, 15.0f, 8.8f, 16.4f, 20.8f, 7.6f, 20.8f)
                circle(12f, 6.0f, 2.0f)
                rays(12f, 6.0f, 2.9f, 4.6f, 1.2f, 270f, 320f, 220f)
            }
        }
    }

    /** LSS018 — embarkation ladder. */
    val LSS018: ImageVector by lazy {
        symbolVector("LSS018") {
            solid {
                bar(7.4f, 2.8f, 7.4f, 21.2f, 1.7f)
                bar(16.6f, 2.8f, 16.6f, 21.2f, 1.7f)
                bar(7.4f, 5.6f, 16.6f, 5.6f, 1.5f)
                bar(7.4f, 9.4f, 16.6f, 9.4f, 1.5f)
                bar(7.4f, 13.2f, 16.6f, 13.2f, 1.5f)
                bar(7.4f, 17.0f, 16.6f, 17.0f, 1.5f)
                bar(7.4f, 20.8f, 16.6f, 20.8f, 1.5f)
            }
        }
    }

    /** LSS019 — marine evacuation slide: deck edge, inclined slide, raft. */
    val LSS019: ImageVector by lazy {
        symbolVector("LSS019") {
            solid {
                rect(2.2f, 3.2f, 10.2f, 5.2f)
                polygon(5.8f, 5.2f, 9.8f, 5.2f, 20.6f, 16.4f, 17.4f, 18.2f)
                roundRect(12.4f, 17.4f, 21.6f, 20.9f, 1.7f)
            }
        }
    }

    /** LSS020 — marine evacuation chute: vertical hooped chute to a raft. */
    val LSS020: ImageVector by lazy {
        symbolVector("LSS020") {
            solid {
                rect(3.0f, 3.0f, 21.0f, 5.0f)
                bar(9.4f, 5.0f, 9.4f, 15.8f, 1.7f)
                bar(14.6f, 5.0f, 14.6f, 15.8f, 1.7f)
                bar(9.4f, 8.0f, 14.6f, 8.0f, 1.0f)
                bar(9.4f, 11.0f, 14.6f, 11.0f, 1.0f)
                bar(9.4f, 14.0f, 14.6f, 14.0f, 1.0f)
                roundRect(4.4f, 16.4f, 19.6f, 20.6f, 2.1f)
            }
        }
    }

    /** LSS021 — immersion suit: hooded figure with mitts and boots. */
    val LSS021: ImageVector by lazy {
        symbolVector("LSS021") {
            solid {
                circle(12f, 5.6f, 3.2f)
                polygon(8.4f, 8.4f, 15.6f, 8.4f, 15.0f, 15.2f, 9.0f, 15.2f)
                bar(8.6f, 9.6f, 4.0f, 14.8f, 2.4f)
                bar(15.4f, 9.6f, 20.0f, 14.8f, 2.4f)
                bar(10.2f, 14.6f, 9.2f, 21.0f, 2.6f)
                bar(13.8f, 14.6f, 14.8f, 21.0f, 2.6f)
            }
        }
    }

    /** LSS022 — liferaft knife. */
    val LSS022: ImageVector by lazy {
        symbolVector("LSS022") {
            solid {
                polygon(9.4f, 14.6f, 20.6f, 3.8f, 21.4f, 8.2f, 12.4f, 17.0f)
                bar(9.0f, 14.0f, 13.0f, 17.6f, 1.5f)
                bar(10.6f, 18.4f, 5.2f, 21.4f, 3.2f)
            }
        }
    }
}

/**
 * The shared lifejacket geometry: collar band over two front panels split by a
 * lacing gap, closed by a waist strap. Scaling it is how the adult / child /
 * infant variants are distinguished.
 */
private fun PathBuilder.vest(cx: Float, top: Float, height: Float) {
    val w = height * 0.52f
    val gap = height * 0.09f
    val panelTop = top + height * 0.10f
    val bottom = top + height
    val taper = height * 0.06f
    arcBand(cx, panelTop + height * 0.03f, height * 0.22f, 180f, 180f, height * 0.13f)
    polygon(cx - w, panelTop, cx - gap, panelTop, cx - gap, bottom, cx - w + taper, bottom)
    polygon(cx + gap, panelTop, cx + w, panelTop, cx + w - taper, bottom, cx + gap, bottom)
    bar(
        cx - w + taper * 0.6f,
        top + height * 0.74f,
        cx + w - taper * 0.6f,
        top + height * 0.74f,
        height * 0.10f,
    )
}
