package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `APP_*` markers with no counterpart in the sign standards: machinery,
 * controls, documents and survival-craft components. Rendered on the neutral
 * slate ground so they never masquerade as certified signage.
 */
object AppMiscSymbols {

    /** Emergency generator. */
    val EMERGENCY_GENERATOR: ImageVector by lazy {
        symbolVector("APP_EMERGENCY_GENERATOR") {
            cutout {
                roundRect(2.6f, 9.0f, 21.4f, 18.6f, 1.6f)
                circle(8.0f, 13.8f, 3.0f)
            }
            solid {
                rect(14.6f, 6.4f, 19.0f, 9.2f)
                rect(4.0f, 18.6f, 7.0f, 20.8f)
                rect(17.0f, 18.6f, 20.0f, 20.8f)
                lightning(3.6f, 2.2f, 5.4f, 6.6f)
            }
        }
    }

    /** Emergency switchboard. */
    val EMERGENCY_SWITCHBOARD: ImageVector by lazy {
        symbolVector("APP_EMERGENCY_SWITCHBOARD") {
            cutout {
                roundRect(2.6f, 5.6f, 21.4f, 21.0f, 1.6f)
                rect(4.4f, 7.6f, 10.4f, 10.0f)
                rect(4.4f, 11.8f, 10.4f, 14.2f)
                rect(4.4f, 16.0f, 10.4f, 18.4f)
                lightning(13.4f, 8.0f, 5.6f, 10.4f)
            }
        }
    }

    /** Emergency battery. */
    val BATTERY: ImageVector by lazy {
        symbolVector("APP_BATTERY") {
            solid {
                rect(6.0f, 3.4f, 9.2f, 5.8f)
                rect(14.8f, 3.4f, 18.0f, 5.8f)
            }
            cutout {
                roundRect(3.4f, 5.6f, 20.6f, 20.6f, 1.6f)
                cross(8.4f, 12.6f, 2.6f, 1.7f)
                rect(13.0f, 11.8f, 18.2f, 13.4f)
            }
        }
    }

    /** Watertight door. */
    val WATERTIGHT_DOOR: ImageVector by lazy {
        symbolVector("APP_WATERTIGHT_DOOR") {
            cutout {
                rect(2.6f, 3.0f, 21.4f, 17.2f)
                rect(4.4f, 4.8f, 19.6f, 15.4f)
            }
            solid {
                rect(4.4f, 4.8f, 11.4f, 15.4f)
                bar(13.2f, 10.1f, 16.8f, 10.1f, 1.4f)
                triangle(16.2f, 8.1f, 19.2f, 10.1f, 16.2f, 12.1f)
                bar(2.6f, 19.2f, 21.4f, 19.2f, 1.4f)
                bar(2.6f, 21.4f, 21.4f, 21.4f, 1.4f)
            }
        }
    }

    /** Skylight closing device. */
    val SKYLIGHT: ImageVector by lazy {
        symbolVector("APP_SKYLIGHT") {
            solid {
                rect(2.6f, 17.6f, 21.4f, 19.8f)
                polygon(6.0f, 11.6f, 18.0f, 11.6f, 20.0f, 17.6f, 4.0f, 17.6f)
                bar(6.4f, 11.4f, 15.6f, 5.8f, 2.0f)
                circle(6.4f, 11.4f, 1.3f)
            }
        }
    }

    /** Fire control plan. */
    val FIRE_CONTROL_PLAN: ImageVector by lazy {
        symbolVector("APP_FIRE_CONTROL_PLAN") {
            cutout {
                roundRect(3.4f, 3.0f, 20.6f, 21.0f, 1.2f)
                rect(5.6f, 5.2f, 12.4f, 6.6f)
                rect(5.6f, 8.2f, 12.4f, 9.6f)
                flame(14.6f, 18.4f, 8.4f, 3.2f)
            }
        }
    }

    /** Muster list. */
    val MUSTER_LIST: ImageVector by lazy {
        symbolVector("APP_MUSTER_LIST") {
            cutout {
                roundRect(4.0f, 2.6f, 20.0f, 21.4f, 1.2f)
                person(12f, 6.0f, 0.62f)
                rect(6.2f, 12.8f, 17.8f, 14.2f)
                rect(6.2f, 15.6f, 17.8f, 17.0f)
                rect(6.2f, 18.4f, 17.8f, 19.8f)
            }
        }
    }

    /** Document / manual. */
    val DOCUMENT: ImageVector by lazy {
        symbolVector("APP_DOCUMENT") {
            cutout {
                polygon(4.0f, 2.6f, 15.0f, 2.6f, 20.0f, 7.6f, 20.0f, 21.4f, 4.0f, 21.4f)
                triangle(15.0f, 2.6f, 20.0f, 7.6f, 15.0f, 7.6f)
                rect(6.4f, 11.0f, 17.6f, 12.4f)
                rect(6.4f, 14.2f, 17.6f, 15.6f)
                rect(6.4f, 17.4f, 17.6f, 18.8f)
            }
        }
    }

    /** SOPEP / SMPEP locker. */
    val SOPEP: ImageVector by lazy {
        symbolVector("APP_SOPEP") {
            solid { rect(2.6f, 3.2f, 21.4f, 5.4f) }
            cutout {
                roundRect(3.4f, 5.4f, 20.6f, 20.6f, 1.6f)
                drop(12f, 8.4f, 9.2f, 3.4f)
            }
        }
    }

    /** Hydrostatic release unit. */
    val HRU: ImageVector by lazy {
        symbolVector("APP_HRU") {
            cutout {
                roundRect(8.8f, 7.6f, 15.2f, 15.6f, 1.6f)
                rect(9.8f, 11.0f, 14.2f, 12.2f)
            }
            solid {
                arcBand(12f, 7.2f, 2.2f, 180f, 180f, 1.4f)
                bar(12f, 15.6f, 12f, 18.4f, 1.2f)
                bar(2.6f, 20.2f, 21.4f, 20.2f, 1.6f)
            }
        }
    }

    /** Pilot ladder. */
    val PILOT_LADDER: ImageVector by lazy {
        symbolVector("APP_PILOT_LADDER") {
            solid {
                bar(7.4f, 2.6f, 7.4f, 21.4f, 1.5f)
                bar(16.6f, 2.6f, 16.6f, 21.4f, 1.5f)
                bar(6.6f, 6.0f, 17.4f, 6.0f, 2.0f)
                bar(6.6f, 10.4f, 17.4f, 10.4f, 2.0f)
                bar(3.0f, 14.8f, 21.0f, 14.8f, 2.4f)
                bar(6.6f, 19.2f, 17.4f, 19.2f, 2.0f)
            }
        }
    }

    /** Davit / launching appliance. */
    val DAVIT: ImageVector by lazy {
        symbolVector("APP_DAVIT") {
            solid {
                bar(5.0f, 20.4f, 5.0f, 8.4f, 2.4f)
                rect(2.4f, 20.0f, 9.6f, 21.4f)
                arcBand(11.0f, 8.4f, 6.0f, 180f, 180f, 2.2f)
                bar(17.0f, 8.4f, 17.0f, 15.0f, 1.1f)
                arcBand(17.0f, 16.6f, 1.9f, 260f, 250f, 1.4f)
            }
        }
    }

    /** Winch. */
    val WINCH: ImageVector by lazy {
        symbolVector("APP_WINCH") {
            solid {
                rect(4.0f, 6.0f, 6.8f, 17.4f)
                rect(17.2f, 6.0f, 20.0f, 17.4f)
                rect(2.6f, 17.4f, 21.4f, 19.8f)
            }
            cutout {
                rect(6.6f, 8.6f, 17.4f, 14.8f)
                rect(8.8f, 9.8f, 10.0f, 13.6f)
                rect(14.0f, 9.8f, 15.2f, 13.6f)
            }
        }
    }

    /** On-load release gear. */
    val RELEASE_GEAR: ImageVector by lazy {
        symbolVector("APP_RELEASE_GEAR") {
            cutout {
                circle(12f, 5.6f, 3.0f)
                circle(12f, 5.6f, 1.5f)
            }
            solid {
                bar(12f, 7.6f, 12f, 13.2f, 2.0f)
                arcBand(12f, 15.8f, 3.6f, 260f, 250f, 2.0f)
            }
        }
    }

    /** Falls / wire ropes. */
    val FALLS: ImageVector by lazy {
        symbolVector("APP_FALLS") {
            cutout {
                bar(4.0f, 20.2f, 17.6f, 6.6f, 2.6f)
                bar(6.0f, 16.4f, 8.0f, 18.4f, 0.9f)
                bar(9.0f, 13.4f, 11.0f, 15.4f, 0.9f)
                bar(12.0f, 10.4f, 14.0f, 12.4f, 0.9f)
                bar(15.0f, 7.4f, 17.0f, 9.4f, 0.9f)
            }
            cutout {
                circle(19.0f, 4.8f, 2.6f)
                circle(19.0f, 4.8f, 1.2f)
            }
        }
    }

    /** Boat engine. */
    val ENGINE: ImageVector by lazy {
        symbolVector("APP_ENGINE") {
            solid { rect(6.0f, 3.6f, 12.0f, 6.2f) }
            cutout {
                roundRect(3.4f, 6.0f, 15.0f, 15.0f, 1.4f)
                rect(5.0f, 7.8f, 8.6f, 9.8f)
            }
            solid {
                bar(14.4f, 12.8f, 18.8f, 17.2f, 1.6f)
                circle(19.2f, 17.8f, 1.3f)
                bar(19.2f, 17.8f, 19.2f, 14.0f, 2.4f)
                bar(16.6f, 19.8f, 19.2f, 17.8f, 2.4f)
                bar(19.2f, 17.8f, 21.6f, 20.0f, 2.4f)
            }
        }
    }

    /** Generic equipment — the fallback marker for an unknown symbol key. */
    val GENERIC: ImageVector by lazy {
        symbolVector("APP_GENERIC") {
            cutout {
                roundRect(3.4f, 3.4f, 20.6f, 20.6f, 3.0f)
                roundRect(6.2f, 6.2f, 17.8f, 17.8f, 1.6f)
            }
            solid { circle(12f, 12f, 2.6f) }
        }
    }
}
