package com.deckwatch.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.deckwatch.core.model.ConditionGrade
import com.deckwatch.core.model.Severity
import com.deckwatch.core.model.TaskStatus

/**
 * Semantic, fixed condition colours — §14. These never change with the theme
 * so that a grade reads identically on deck, at night and on the bridge.
 */
object ConditionColors {
    val Good = Color(0xFF1B873F)
    val Acceptable = Color(0xFF6FA82C)
    val Monitor = Color(0xFFE8A317)
    val Defective = Color(0xFFE5661B)
    val OutOfService = Color(0xFFC2261B)
    val NotChecked = Color(0xFF8A8F98)

    fun of(grade: ConditionGrade): Color = when (grade) {
        ConditionGrade.GOOD -> Good
        ConditionGrade.ACCEPTABLE -> Acceptable
        ConditionGrade.MONITOR -> Monitor
        ConditionGrade.DEFECTIVE -> Defective
        ConditionGrade.OUT_OF_SERVICE -> OutOfService
        ConditionGrade.NOT_CHECKED -> NotChecked
    }

    fun of(status: TaskStatus): Color = when (status) {
        TaskStatus.OVERDUE -> OutOfService
        TaskStatus.DUE_SOON -> Monitor
        TaskStatus.PENDING -> NotChecked
        TaskStatus.DONE -> Good
        TaskStatus.SKIPPED -> Defective
        TaskStatus.NOT_APPLICABLE -> NotChecked
    }

    fun of(severity: Severity): Color = when (severity) {
        Severity.OBSERVATION -> NotChecked
        Severity.MINOR -> Monitor
        Severity.MAJOR -> Defective
        Severity.CRITICAL_DETAINABLE -> OutOfService
    }
}

/** Signage grounds — used for symbol tiles only, never as UI chrome — §14. */
object SignageColors {
    val LsaGreen = Color(0xFF009639)
    val FfeRed = Color(0xFFC8102E)
    val MandatoryBlue = Color(0xFF005EB8)
    val WarningYellow = Color(0xFFFFD100)

    /** Fire-control-plan media colour code — §10.1. */
    val MediaCo2Grey = Color(0xFF8A8F98)
    val MediaOtherGasBrown = Color(0xFF8B5A2B)
    val MediaPowderWhite = Color(0xFFF2F2F2)
    val MediaFoamYellow = Color(0xFFF0C419)
    val MediaWaterGreen = Color(0xFF2E8B57)
    val MediaSprinklerOrange = Color(0xFFE8731A)
}

/** Structural palette: dark navy / slate neutral ramp + one signal-amber accent. */
internal object Palette {
    val Navy950 = Color(0xFF0A0E14)
    val Navy900 = Color(0xFF10151F)
    val Navy850 = Color(0xFF161D2A)
    val Navy800 = Color(0xFF1C2536)
    val Navy700 = Color(0xFF283449)
    val Navy600 = Color(0xFF3A4A63)
    val Slate500 = Color(0xFF5C6779)
    val Slate400 = Color(0xFF7D8797)
    val Slate300 = Color(0xFFA3ABB8)
    val Slate200 = Color(0xFFC9CED8)
    val Slate100 = Color(0xFFE4E7EC)
    val Slate50 = Color(0xFFF4F5F7)
    val White = Color(0xFFFFFFFF)

    val Amber = Color(0xFFFFB000)
    val AmberDeep = Color(0xFF8A5B00)

    // Bridge (red night-vision) ramp — no white above 40% luminance.
    val BridgeRed = Color(0xFFB01818)
    val BridgeRedDim = Color(0xFF7A1010)
    val BridgeOnDark = Color(0xFF992222)
    val BridgeSurface = Color(0xFF120404)
    val BridgeSurfaceHigh = Color(0xFF1C0808)
}
