package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

/**
 * `APP_*` markers that belong to the **fire-fighting** series (red ground) —
 * the ISO 7010 red-set extensions and the machinery the standards leave to the
 * fire control plan. Drawn from scratch, same conventions as [FesSymbols].
 */
object AppFireSymbols {

    /** Fire blanket: wall container with the pull tab hanging out. */
    val FIRE_BLANKET: ImageVector by lazy {
        symbolVector("APP_FIRE_BLANKET") {
            cutout {
                roundRect(4.6f, 3.4f, 19.4f, 13.4f, 1.8f)
                rect(9.4f, 5.6f, 14.6f, 8.4f)
            }
            solid {
                polygon(10.2f, 13.4f, 13.8f, 13.4f, 13.2f, 20.6f, 10.8f, 20.6f)
            }
        }
    }

    /** Fire hydrant: post with two side outlets. */
    val FIRE_HYDRANT: ImageVector by lazy {
        symbolVector("APP_FIRE_HYDRANT") {
            solid {
                roundRect(9.8f, 6.6f, 14.2f, 19.6f, 1.6f)
                domeUp(12f, 6.8f, 2.6f)
                circle(12f, 3.8f, 1.0f)
                bar(6.2f, 11.6f, 9.9f, 11.6f, 2.4f)
                bar(14.1f, 11.6f, 17.8f, 11.6f, 2.4f)
                rect(5.0f, 10.0f, 6.6f, 13.2f)
                rect(17.4f, 10.0f, 19.0f, 13.2f)
                roundRect(6.8f, 19.2f, 17.2f, 21.4f, 0.8f)
            }
        }
    }

    /** Fire hose: flaked coil led off to a nozzle. */
    val FIRE_HOSE: ImageVector by lazy {
        symbolVector("APP_FIRE_HOSE") {
            solid {
                arcBand(8.2f, 12.6f, 4.2f, 180f, 180f, 2.8f)
                arcBand(16.6f, 12.6f, 4.2f, 0f, 180f, 2.8f)
                rect(2.6f, 10.0f, 4.8f, 15.2f)
                rect(20.0f, 10.0f, 22.2f, 15.2f)
            }
        }
    }

    /** Fire nozzle: dual-purpose jet/spray branch. */
    val FIRE_NOZZLE: ImageVector by lazy {
        symbolVector("APP_FIRE_NOZZLE") {
            solid {
                rect(2.6f, 8.4f, 4.6f, 15.6f)
                polygon(4.2f, 9.4f, 13.4f, 10.8f, 13.4f, 13.2f, 4.2f, 14.6f)
                polygon(13.4f, 10.8f, 16.4f, 11.5f, 16.4f, 12.5f, 13.4f, 13.2f)
                rays(17.2f, 12.0f, 0.8f, 4.4f, 1.2f, 330f, 0f, 30f)
            }
        }
    }

    /** Firefighter's portable radio: belt-clipped explosion-proof set. */
    val FF_RADIO: ImageVector by lazy {
        symbolVector("APP_FF_RADIO") {
            cutout {
                roundRect(6.6f, 5.4f, 16.0f, 21.0f, 1.8f)
                rect(8.2f, 7.2f, 14.4f, 10.0f)
                circle(9.9f, 13.4f, 1.0f)
                circle(12.7f, 13.4f, 1.0f)
                circle(9.9f, 16.4f, 1.0f)
                circle(12.7f, 16.4f, 1.0f)
            }
            solid {
                bar(13.8f, 5.6f, 13.8f, 2.6f, 1.6f)
                circle(9.4f, 4.4f, 1.4f)
                bar(16.0f, 9.0f, 18.4f, 9.0f, 1.4f)
                bar(17.9f, 8.6f, 17.9f, 15.0f, 1.4f)
            }
        }
    }

    /** Fire alarm bell. */
    val FIRE_ALARM_BELL: ImageVector by lazy {
        symbolVector("APP_FIRE_ALARM_BELL") {
            solid {
                rect(11.0f, 4.2f, 13.0f, 9.4f)
                domeUp(12f, 15.0f, 6.4f)
                roundRect(4.4f, 15.0f, 19.6f, 17.2f, 0.8f)
                circle(12f, 19.2f, 1.9f)
            }
        }
    }

    /** Fire alarm flashing light: beacon with rays. */
    val FIRE_ALARM_LIGHT: ImageVector by lazy {
        symbolVector("APP_FIRE_ALARM_LIGHT") {
            solid {
                polygon(9.0f, 9.8f, 15.0f, 9.8f, 16.4f, 17.0f, 7.6f, 17.0f)
                domeUp(12f, 10.0f, 3.0f)
                roundRect(7.4f, 17.0f, 16.6f, 20.4f, 1.0f)
                rays(12f, 9.2f, 4.8f, 7.0f, 1.4f, 270f, 315f, 225f)
            }
        }
    }

    /** Fire detection and alarm panel. */
    val DETECTION_PANEL: ImageVector by lazy {
        symbolVector("APP_DETECTION_PANEL") {
            cutout {
                roundRect(2.6f, 4.0f, 21.4f, 20.0f, 1.8f)
                rect(4.6f, 6.0f, 19.4f, 10.2f)
                circle(6.4f, 13.0f, 1.1f)
                circle(9.6f, 13.0f, 1.1f)
                circle(12.8f, 13.0f, 1.1f)
                circle(16.0f, 13.0f, 1.1f)
                roundRect(5.0f, 15.4f, 10.6f, 18.0f, 1.0f)
                circle(17.4f, 16.7f, 1.8f)
            }
        }
    }

    /** Smoke detector: deckhead head with smoke rising. */
    val SMOKE_DETECTOR: ImageVector by lazy {
        symbolVector("APP_SMOKE_DETECTOR") {
            solid {
                rect(2.6f, 3.0f, 21.4f, 4.8f)
                domeDown(12f, 4.8f, 5.0f)
                circle(12f, 10.4f, 1.1f)
                arcBand(9.6f, 15.6f, 2.0f, 90f, 180f, 1.3f)
                arcBand(9.6f, 19.6f, 2.0f, 270f, 180f, 1.3f)
                arcBand(16.4f, 16.6f, 1.6f, 90f, 180f, 1.2f)
                arcBand(16.4f, 19.8f, 1.6f, 270f, 180f, 1.2f)
            }
        }
    }

    /** Heat detector: deckhead head with rising heat. */
    val HEAT_DETECTOR: ImageVector by lazy {
        symbolVector("APP_HEAT_DETECTOR") {
            solid {
                rect(2.6f, 3.0f, 21.4f, 4.8f)
                domeDown(12f, 4.8f, 5.0f)
                heatArrow(7.4f)
                heatArrow(12f)
                heatArrow(16.6f)
            }
        }
    }

    /** Flame detector: deckhead head watching a flame. */
    val FLAME_DETECTOR: ImageVector by lazy {
        symbolVector("APP_FLAME_DETECTOR") {
            solid {
                rect(2.6f, 3.0f, 21.4f, 4.8f)
                domeDown(12f, 4.8f, 5.0f)
                flame(12f, 21.2f, 9.4f, 3.8f)
            }
        }
    }

    /** Gas detector: portable meter sampling gas. */
    val GAS_DETECTOR: ImageVector by lazy {
        symbolVector("APP_GAS_DETECTOR") {
            cutout {
                roundRect(5.6f, 7.6f, 14.8f, 21.2f, 1.8f)
                rect(7.1f, 9.6f, 13.3f, 13.2f)
                circle(10.2f, 16.8f, 1.5f)
            }
            solid {
                roundRect(8.2f, 4.6f, 12.2f, 7.8f, 1.0f)
                circle(17.4f, 9.8f, 1.5f)
                circle(20.0f, 6.8f, 1.2f)
                circle(16.8f, 5.0f, 1.0f)
            }
        }
    }

    /** Main fire pump: volute, motor and seating. */
    val FIRE_PUMP: ImageVector by lazy {
        symbolVector("APP_FIRE_PUMP") {
            cutout {
                circle(9.0f, 13.0f, 5.4f)
                circle(9.0f, 13.0f, 1.8f)
            }
            solid {
                roundRect(13.4f, 10.0f, 20.8f, 16.0f, 1.0f)
                bar(9.0f, 8.0f, 9.0f, 4.8f, 2.6f)
                rect(6.6f, 3.4f, 11.4f, 5.0f)
                rect(3.0f, 18.4f, 21.0f, 20.6f)
            }
        }
    }

    /** Emergency fire pump: the pump marked with the emergency-power bolt. */
    val EMERGENCY_FIRE_PUMP: ImageVector by lazy {
        symbolVector("APP_EMERGENCY_FIRE_PUMP") {
            cutout {
                circle(8.2f, 14.6f, 4.6f)
                circle(8.2f, 14.6f, 1.5f)
            }
            solid {
                roundRect(12.0f, 12.0f, 19.4f, 17.4f, 1.0f)
                bar(8.2f, 10.4f, 8.2f, 8.0f, 2.2f)
                rect(6.0f, 6.8f, 10.4f, 8.2f)
                rect(3.0f, 19.4f, 21.0f, 21.2f)
                lightning(13.6f, 2.4f, 6.4f, 8.0f)
            }
        }
    }

    /** International shore connection: bolted flange on a stub pipe. */
    val ISC: ImageVector by lazy {
        symbolVector("APP_ISC") {
            solid {
                bar(2.4f, 12.0f, 6.4f, 12.0f, 3.4f)
            }
            cutout {
                circle(13.2f, 12.0f, 7.8f)
                circle(13.2f, 12.0f, 3.6f)
                circle(13.2f, 6.2f, 1.1f)
                circle(13.2f, 17.8f, 1.1f)
                circle(19.0f, 12.0f, 1.1f)
                circle(7.4f, 12.0f, 1.1f)
            }
        }
    }

    /** Section valve: bowtie body with a handwheel. */
    val SECTION_VALVE: ImageVector by lazy {
        symbolVector("APP_SECTION_VALVE") {
            solid {
                triangle(4.0f, 8.4f, 11.4f, 12.6f, 4.0f, 16.8f)
                triangle(12.6f, 12.6f, 20.0f, 8.4f, 20.0f, 16.8f)
                bar(12f, 13.4f, 12f, 5.8f, 1.7f)
                bar(7.8f, 5.0f, 16.2f, 5.0f, 1.9f)
            }
        }
    }

    /** Sprinkler head: pipe, deflector and spray. */
    val SPRINKLER: ImageVector by lazy {
        symbolVector("APP_SPRINKLER") {
            solid {
                bar(12f, 2.8f, 12f, 7.2f, 2.0f)
                rect(9.4f, 6.6f, 14.6f, 8.4f)
                roundRect(10.2f, 8.4f, 13.8f, 12.0f, 0.8f)
                roundRect(7.6f, 12.0f, 16.4f, 13.8f, 0.8f)
                rays(12f, 14.2f, 1.4f, 6.4f, 1.4f, 60f, 90f, 120f)
            }
        }
    }

    /** CO2 cylinder bank: racked cylinders. */
    val CO2_BANK: ImageVector by lazy {
        symbolVector("APP_CO2_BANK") {
            solid {
                bar(2.6f, 7.6f, 21.4f, 7.6f, 1.5f)
                bar(2.6f, 18.4f, 21.4f, 18.4f, 1.5f)
                roundRect(3.9f, 5.2f, 7.3f, 20.6f, 1.7f)
                roundRect(8.1f, 5.2f, 11.5f, 20.6f, 1.7f)
                roundRect(12.5f, 5.2f, 15.9f, 20.6f, 1.7f)
                roundRect(16.9f, 5.2f, 20.3f, 20.6f, 1.7f)
                bar(5.6f, 3.4f, 5.6f, 5.4f, 1.1f)
                bar(9.8f, 3.4f, 9.8f, 5.4f, 1.1f)
                bar(14.2f, 3.4f, 14.2f, 5.4f, 1.1f)
                bar(18.6f, 3.4f, 18.6f, 5.4f, 1.1f)
            }
        }
    }

    /** Foam system: concentrate tank, line and foam. */
    val FOAM_SYSTEM: ImageVector by lazy {
        symbolVector("APP_FOAM_SYSTEM") {
            solid {
                roundRect(2.6f, 8.4f, 10.6f, 19.8f, 1.6f)
                bar(10.2f, 11.0f, 15.0f, 11.0f, 1.6f)
                bar(15.0f, 11.0f, 15.0f, 13.2f, 1.6f)
                triangle(13.0f, 12.8f, 17.0f, 12.8f, 15.0f, 15.4f)
                circle(14.2f, 18.2f, 2.0f)
                circle(18.4f, 17.2f, 1.6f)
                circle(17.4f, 20.6f, 1.4f)
                circle(20.8f, 19.8f, 1.2f)
            }
        }
    }

    /** Inert gas system: scrubber column with trays. */
    val INERT_GAS: ImageVector by lazy {
        symbolVector("APP_INERT_GAS") {
            cutout {
                roundRect(7.0f, 3.6f, 17.0f, 21.0f, 2.2f)
                rect(8.6f, 8.0f, 15.4f, 9.2f)
                rect(8.6f, 12.0f, 15.4f, 13.2f)
                rect(8.6f, 16.0f, 15.4f, 17.2f)
            }
            solid {
                bar(16.6f, 6.0f, 21.0f, 6.0f, 1.6f)
                bar(3.0f, 18.6f, 7.4f, 18.6f, 1.6f)
            }
        }
    }

    /** Galley hood extinguishing system: hood, nozzle and range. */
    val GALLEY_HOOD: ImageVector by lazy {
        symbolVector("APP_GALLEY_HOOD") {
            solid {
                polygon(3.6f, 4.0f, 20.4f, 4.0f, 17.4f, 10.0f, 6.6f, 10.0f)
                bar(12f, 10.0f, 12f, 12.0f, 1.2f)
                circle(12f, 12.6f, 1.1f)
                circle(8.6f, 15.8f, 1.7f)
                circle(15.4f, 15.8f, 1.7f)
                rect(4.6f, 17.8f, 19.4f, 19.8f)
            }
        }
    }

    /** Fire door: self-closing A/B-class door with a flame alongside. */
    val FIRE_DOOR: ImageVector by lazy {
        symbolVector("APP_FIRE_DOOR") {
            cutout {
                rect(3.0f, 3.0f, 14.8f, 21.2f)
                rect(4.8f, 4.8f, 13.0f, 21.2f)
            }
            solid {
                circle(11.4f, 12.8f, 1.1f)
                flame(18.8f, 18.2f, 8.6f, 3.0f)
            }
        }
    }

    /** Fire damper: duct with the blade closing across it. */
    val FIRE_DAMPER: ImageVector by lazy {
        symbolVector("APP_FIRE_DAMPER") {
            solid {
                bar(2.6f, 6.6f, 21.4f, 6.6f, 1.8f)
                bar(2.6f, 17.4f, 21.4f, 17.4f, 1.8f)
                bar(7.4f, 16.2f, 16.6f, 7.8f, 2.2f)
            }
            cutout {
                circle(12f, 12f, 2.2f)
                circle(12f, 12f, 1.0f)
            }
        }
    }

    /** Ventilation stop: fan with its stop control. */
    val VENT_STOP: ImageVector by lazy {
        symbolVector("APP_VENT_STOP") {
            cutout {
                circle(12f, 10.6f, 8.0f)
                circle(12f, 10.6f, 6.8f)
            }
            solid {
                circle(12f, 10.6f, 1.7f)
                bar(12f, 10.6f, 12f, 4.8f, 2.6f)
                bar(12f, 10.6f, 17.0f, 13.5f, 2.6f)
                bar(7.0f, 13.5f, 12f, 10.6f, 2.6f)
                roundRect(3.2f, 19.4f, 20.8f, 21.6f, 1.0f)
            }
        }
    }

    /** Quick-closing valve: bowtie body with the remote closing arrow. */
    val QUICK_CLOSING_VALVE: ImageVector by lazy {
        symbolVector("APP_QUICK_CLOSING_VALVE") {
            solid {
                triangle(3.4f, 10.4f, 10.8f, 14.6f, 3.4f, 18.8f)
                triangle(12.0f, 14.6f, 19.4f, 10.4f, 19.4f, 18.8f)
                bar(11.4f, 15.4f, 11.4f, 8.4f, 1.7f)
                bar(7.4f, 7.6f, 15.4f, 7.6f, 1.9f)
                bar(20.4f, 3.0f, 20.4f, 6.4f, 1.4f)
                triangle(18.8f, 6.0f, 22.0f, 6.0f, 20.4f, 8.6f)
            }
        }
    }

    /** SCBA set: cylinder, hose and full face mask. */
    val SCBA: ImageVector by lazy {
        symbolVector("APP_SCBA") {
            solid {
                roundRect(3.4f, 5.4f, 9.4f, 20.0f, 3.0f)
                rect(5.4f, 3.2f, 7.4f, 5.8f)
                bar(9.2f, 14.6f, 13.0f, 14.6f, 1.6f)
            }
            cutout {
                roundRect(12.4f, 7.6f, 20.8f, 17.6f, 3.4f)
                roundRect(14.0f, 9.6f, 19.2f, 12.8f, 1.4f)
            }
        }
    }

    /** Fireman's outfit: helmet with brim and neck flap. */
    val FIREMANS_OUTFIT: ImageVector by lazy {
        symbolVector("APP_FIREMANS_OUTFIT") {
            solid {
                domeUp(12f, 14.0f, 5.6f)
                polygon(3.4f, 14.0f, 20.6f, 14.0f, 19.0f, 17.0f, 5.0f, 17.0f)
                polygon(9.4f, 17.0f, 14.6f, 17.0f, 13.6f, 20.8f, 10.4f, 20.8f)
            }
        }
    }

    /** Safety lamp: caged hand lamp. */
    val SAFETY_LAMP: ImageVector by lazy {
        symbolVector("APP_SAFETY_LAMP") {
            solid {
                arcBand(12f, 6.6f, 3.2f, 180f, 180f, 1.3f)
                roundRect(7.6f, 6.6f, 16.4f, 9.0f, 0.8f)
                roundRect(6.6f, 17.4f, 17.4f, 20.6f, 0.8f)
            }
            cutout {
                polygon(8.4f, 9.0f, 15.6f, 9.0f, 16.8f, 17.4f, 7.2f, 17.4f)
                rect(10.1f, 10.4f, 11.1f, 16.0f)
                rect(12.9f, 10.4f, 13.9f, 16.0f)
            }
        }
    }

    /** Fireman's axe. */
    val FIRE_AXE: ImageVector by lazy {
        symbolVector("APP_FIRE_AXE") {
            solid {
                bar(4.6f, 20.8f, 15.4f, 7.4f, 2.0f)
                polygon(13.9f, 6.6f, 15.0f, 3.0f, 19.8f, 6.9f, 16.5f, 8.6f)
            }
        }
    }
}

/** One rising-heat arrow for the heat detector, at column [cx]. */
private fun PathBuilder.heatArrow(cx: Float) {
    bar(cx, 20.8f, cx, 14.8f, 1.5f)
    triangle(cx - 1.7f, 15.2f, cx, 12.4f, cx + 1.7f, 15.2f)
}
