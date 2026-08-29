package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `EES` — emergency and safety equipment pictograms (green ground),
 * drawn from scratch to the ISO 3864-1 shape/colour convention.
 */
object EesSymbols {

    /** EES001 — first aid. */
    val EES001: ImageVector by lazy {
        symbolVector("EES001") {
            solid { cross(12f, 12f, 8.6f, 6.8f) }
        }
    }

    /** EES002 — emergency telephone: handset. */
    val EES002: ImageVector by lazy {
        symbolVector("EES002") {
            solid {
                bar(8.0f, 16.0f, 16.0f, 8.0f, 2.6f)
                bar(4.6f, 15.4f, 8.6f, 19.4f, 4.4f)
                bar(15.4f, 4.6f, 19.4f, 8.6f, 4.4f)
            }
        }
    }

    /** EES003 — eyewash station: eye over the bowl with two jets. */
    val EES003: ImageVector by lazy {
        symbolVector("EES003") {
            cutout {
                lens(12f, 9.6f, 6.4f, 2.2f)
                circle(12f, 9.6f, 1.9f)
            }
            solid {
                bar(9.6f, 16.4f, 10.8f, 13.4f, 1.3f)
                bar(13.2f, 13.4f, 14.4f, 16.4f, 1.3f)
                arcBand(12f, 17.0f, 4.0f, 0f, 180f, 1.6f)
            }
        }
    }

    /** EES004 — safety shower. */
    val EES004: ImageVector by lazy {
        symbolVector("EES004") {
            solid {
                bar(12f, 2.6f, 12f, 6.6f, 1.8f)
                polygon(6.4f, 6.4f, 17.6f, 6.4f, 15.6f, 9.4f, 8.4f, 9.4f)
                rays(12f, 10.0f, 1.2f, 9.4f, 1.4f, 62f, 76f, 90f, 104f, 118f)
            }
        }
    }

    /** EES005 — stretcher. */
    val EES005: ImageVector by lazy {
        symbolVector("EES005") {
            solid {
                bar(2.4f, 8.6f, 21.6f, 8.6f, 1.6f)
                bar(2.4f, 16.6f, 21.6f, 16.6f, 1.6f)
                bar(4.2f, 8.6f, 4.2f, 16.6f, 1.4f)
                bar(19.8f, 8.6f, 19.8f, 16.6f, 1.4f)
                circle(7.8f, 12.6f, 2.2f)
                roundRect(10.6f, 10.6f, 18.4f, 14.6f, 2.0f)
            }
        }
    }

    /** EES006 — medical grab bag. */
    val EES006: ImageVector by lazy {
        symbolVector("EES006") {
            solid { arcBand(12f, 8.2f, 3.0f, 180f, 180f, 1.3f) }
            cutout {
                roundRect(3.6f, 8.2f, 20.4f, 20.6f, 2.0f)
                cross(12f, 14.4f, 3.6f, 2.6f)
            }
        }
    }

    /** EES007 — oxygen resuscitator: bag, valve and mask. */
    val EES007: ImageVector by lazy {
        symbolVector("EES007") {
            solid {
                roundRect(2.6f, 8.0f, 11.4f, 16.0f, 4.0f)
                rect(11.2f, 11.0f, 14.2f, 13.0f)
                polygon(14.0f, 10.0f, 19.4f, 7.0f, 19.4f, 17.0f, 14.0f, 14.0f)
            }
        }
    }

    /** EES008 — emergency escape breathing device: hood and cylinder. */
    val EES008: ImageVector by lazy {
        symbolVector("EES008") {
            solid {
                circle(14.4f, 11.0f, 3.2f)
                arcBand(14.4f, 11.0f, 5.0f, 150f, 240f, 1.7f)
                roundRect(2.6f, 13.0f, 7.0f, 20.6f, 2.0f)
                bar(6.6f, 14.2f, 10.2f, 12.6f, 1.5f)
            }
        }
    }

    /** EES009 — doctor. */
    val EES009: ImageVector by lazy {
        symbolVector("EES009") {
            solid {
                person(10.4f, 6.6f, 1.25f)
                cross(18.0f, 16.2f, 3.2f, 2.4f)
            }
        }
    }

    /** EES010 — automated external defibrillator. */
    val EES010: ImageVector by lazy {
        symbolVector("EES010") {
            cutout {
                heart(12f, 6.0f, 20.8f, 8.8f)
                lightning(9.4f, 8.4f, 5.2f, 8.6f)
            }
        }
    }

    /** EES012 — general alarm. */
    val EES012: ImageVector by lazy {
        symbolVector("EES012") {
            solid {
                rect(11.2f, 5.0f, 12.8f, 9.4f)
                domeUp(12f, 14.8f, 5.4f)
                roundRect(5.8f, 14.8f, 18.2f, 16.8f, 0.8f)
                circle(12f, 18.6f, 1.7f)
                arcBand(12f, 13.4f, 8.0f, 200f, 42f, 1.3f)
                arcBand(12f, 13.4f, 8.0f, 298f, 42f, 1.3f)
            }
        }
    }

    /** EES013 — break to obtain access. */
    val EES013: ImageVector by lazy {
        symbolVector("EES013") {
            cutout {
                rect(2.6f, 4.0f, 15.4f, 17.2f)
                rect(4.4f, 5.8f, 13.6f, 15.4f)
            }
            solid {
                lightning(6.6f, 6.4f, 4.8f, 8.4f)
                roundRect(15.8f, 13.2f, 21.4f, 16.4f, 0.8f)
                bar(18.6f, 16.4f, 18.6f, 21.4f, 1.7f)
            }
        }
    }
}
