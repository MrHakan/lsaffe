package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `FES` — fire-fighting equipment pictograms, drawn from scratch (§10.2).
 * White on a transparent ground; the tile supplies the red ISO 3864-1 ground,
 * or a media colour for the tintable members of the series (§10.3).
 */
object FesSymbols {

    /** FES001 — portable fire extinguisher: bottle, lever, hose, nozzle. */
    val FES001: ImageVector by lazy {
        symbolVector("FES001") {
            solid {
                roundRect(8.4f, 8.2f, 15.4f, 21.2f, 2.0f)
                rect(10.4f, 5.8f, 13.4f, 8.6f)
                bar(9.8f, 4.9f, 15.8f, 3.7f, 1.5f)
                bar(11.9f, 4.4f, 11.9f, 6.2f, 1.3f)
                bar(14.8f, 6.2f, 18.6f, 9.4f, 1.4f)
                triangle(17.8f, 8.3f, 21.0f, 10.0f, 17.6f, 11.6f)
            }
        }
    }

    /** FES002 — fire hose reel: spoked drum with the hose led off to a nozzle. */
    val FES002: ImageVector by lazy {
        symbolVector("FES002") {
            cutout {
                circle(10.6f, 12.4f, 8.0f)
                circle(10.6f, 12.4f, 4.2f)
            }
            solid {
                circle(10.6f, 12.4f, 2.0f)
                rays(10.6f, 12.4f, 1.4f, 4.8f, 1.3f, 0f, 90f, 180f, 270f)
                bar(15.6f, 17.4f, 19.4f, 20.0f, 1.5f)
                triangle(18.8f, 18.8f, 21.4f, 20.6f, 18.4f, 21.4f)
            }
        }
    }

    /** FES003 — collection of fire-fighting equipment: the fire locker. */
    val FES003: ImageVector by lazy {
        symbolVector("FES003") {
            cutout {
                roundRect(2.6f, 4.4f, 21.4f, 19.6f, 1.8f)
                roundRect(4.4f, 6.2f, 19.6f, 17.8f, 1.0f)
            }
            solid {
                roundRect(6.8f, 9.0f, 10.0f, 16.0f, 1.1f)
                rect(7.8f, 7.6f, 9.0f, 9.2f)
            }
            cutout {
                circle(15.2f, 12.4f, 3.4f)
                circle(15.2f, 12.4f, 1.7f)
            }
        }
    }

    /** FES004 — fire alarm call point: break-glass box being pressed. */
    val FES004: ImageVector by lazy {
        symbolVector("FES004") {
            cutout {
                roundRect(4.6f, 3.4f, 19.4f, 17.2f, 1.8f)
                roundRect(6.4f, 5.2f, 17.6f, 15.4f, 1.0f)
            }
            solid {
                circle(12f, 10.3f, 3.0f)
                bar(12f, 21.4f, 12f, 18.4f, 2.6f)
                triangle(10.0f, 18.8f, 12f, 16.4f, 14.0f, 18.8f)
            }
        }
    }

    /** FES005 — fixed fire-extinguishing battery: manifolded cylinder bank. */
    val FES005: ImageVector by lazy {
        symbolVector("FES005") {
            solid {
                bar(3.4f, 5.6f, 20.6f, 5.6f, 1.6f)
                bar(6.3f, 5.6f, 6.3f, 7.6f, 1.2f)
                bar(12f, 5.6f, 12f, 7.6f, 1.2f)
                bar(17.7f, 5.6f, 17.7f, 7.6f, 1.2f)
                roundRect(4.2f, 7.0f, 8.4f, 20.6f, 2.0f)
                roundRect(9.9f, 7.0f, 14.1f, 20.6f, 2.0f)
                roundRect(15.6f, 7.0f, 19.8f, 20.6f, 2.0f)
            }
        }
    }

    /** FES006 — wheeled fire extinguisher: bottle on a trolley. */
    val FES006: ImageVector by lazy {
        symbolVector("FES006") {
            solid {
                roundRect(6.4f, 5.4f, 13.4f, 16.6f, 2.2f)
                bar(13.0f, 7.4f, 18.2f, 10.0f, 1.3f)
                triangle(17.6f, 8.8f, 20.6f, 10.2f, 17.4f, 12.0f)
                bar(5.2f, 16.4f, 3.6f, 6.2f, 1.4f)
                bar(2.6f, 5.6f, 5.8f, 4.8f, 1.4f)
                bar(5.6f, 17.2f, 15.0f, 17.2f, 1.3f)
            }
            cutout {
                circle(7.0f, 19.4f, 2.4f)
                circle(7.0f, 19.4f, 0.9f)
                circle(13.8f, 19.4f, 2.4f)
                circle(13.8f, 19.4f, 0.9f)
            }
        }
    }

    /** FES007 — portable foam applicator: canister, branch pipe, foam. */
    val FES007: ImageVector by lazy {
        symbolVector("FES007") {
            solid {
                roundRect(3.0f, 10.6f, 9.4f, 20.8f, 1.6f)
                bar(8.8f, 12.8f, 16.2f, 7.6f, 1.8f)
                triangle(15.4f, 5.8f, 18.8f, 7.4f, 16.2f, 9.6f)
                circle(19.8f, 4.6f, 1.5f)
                circle(21.0f, 7.4f, 1.1f)
                circle(17.4f, 3.2f, 1.1f)
            }
        }
    }

    /** FES008 — water fog applicator: lance with a bent tip and a fan spray. */
    val FES008: ImageVector by lazy {
        symbolVector("FES008") {
            solid {
                bar(3.4f, 20.6f, 13.0f, 11.0f, 1.9f)
                bar(12.6f, 11.4f, 17.2f, 9.2f, 1.9f)
                rays(17.8f, 8.8f, 1.2f, 4.6f, 1.2f, 300f, 340f, 20f)
            }
        }
    }

    /** FES009 — fixed fire-extinguishing installation: bottle, main, nozzle. */
    val FES009: ImageVector by lazy {
        symbolVector("FES009") {
            solid {
                roundRect(2.6f, 6.6f, 7.4f, 16.6f, 2.2f)
                rect(4.0f, 4.4f, 6.0f, 7.0f)
                bar(6.6f, 9.0f, 16.4f, 9.0f, 1.5f)
                bar(16.4f, 9.0f, 16.4f, 12.0f, 1.5f)
                triangle(14.4f, 11.6f, 18.4f, 11.6f, 16.4f, 14.0f)
                rays(16.4f, 14.6f, 1.2f, 4.4f, 1.1f, 60f, 90f, 120f)
            }
        }
    }

    /** FES010 — fixed fire-extinguishing bottle: strapped cylinder. */
    val FES010: ImageVector by lazy {
        symbolVector("FES010") {
            cutout {
                roundRect(7.6f, 6.4f, 16.4f, 21.2f, 3.2f)
                rect(9.0f, 17.4f, 15.0f, 18.8f)
            }
            solid {
                rect(10.8f, 3.6f, 13.2f, 7.0f)
                bar(9.6f, 3.2f, 14.4f, 3.2f, 1.8f)
            }
        }
    }

    /** FES011 — remote release station: pull handle and release arrow in a box. */
    val FES011: ImageVector by lazy {
        symbolVector("FES011") {
            cutout {
                roundRect(3.4f, 4.4f, 20.6f, 19.6f, 1.8f)
                roundRect(5.2f, 6.2f, 18.8f, 17.8f, 1.0f)
            }
            solid {
                circle(9.0f, 9.8f, 2.0f)
                bar(9.0f, 9.8f, 9.0f, 15.4f, 1.6f)
                bar(15.4f, 8.4f, 15.4f, 13.2f, 1.5f)
                triangle(13.4f, 12.8f, 17.4f, 12.8f, 15.4f, 15.6f)
            }
        }
    }

    /** FES012 — fire monitor: pedestal, barrel and jet. */
    val FES012: ImageVector by lazy {
        symbolVector("FES012") {
            solid {
                polygon(8.6f, 16.2f, 15.4f, 16.2f, 17.2f, 20.8f, 6.8f, 20.8f)
                circle(12f, 15.0f, 2.6f)
                bar(12f, 14.6f, 18.0f, 9.2f, 2.6f)
                rays(18.8f, 8.4f, 1.0f, 3.2f, 1.2f, 300f, 335f, 10f)
            }
        }
    }
}
