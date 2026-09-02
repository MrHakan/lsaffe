package com.deckwatch.feature.report

import com.deckwatch.core.designsystem.symbols.SymbolLibrary
import com.deckwatch.core.model.ConditionGrade

/**
 * Colours for the exported plan, as CSS hex strings.
 *
 * ### Why the markers are glyphs, not the app's pictograms
 * The app draws markers from [SymbolLibrary]'s `ImageVector`s. Those are Compose objects: turning
 * one into SVG path data would mean re-implementing the vector-to-path conversion, and the result
 * would be several kilobytes of path per marker on a plan that can carry 600 of them (§17.3). A
 * printed deck sheet does not need the pictogram — it needs to be unambiguous next to the legend
 * table beside it. So an exported marker is **a rounded square in the symbol's series ground
 * colour carrying the marker's number**, and the legend row spells out tag, type and condition.
 * That is the §13.3 requirement ("numbered markers plus a legend table") read literally.
 *
 * The *ground colour* still comes from the real library, so an exported plan is colour-consistent
 * with the app: green for life-saving, red for fire-fighting, slate for the app's own markers
 * (§14 — signage colours are for symbol grounds only, never for chrome).
 */
internal object MarkerPalette {

    /** ISO 3864-1 / §14 signage grounds, matching `SymbolLibrary.groundColor`. */
    const val LSA_GREEN: String = "#009639"
    const val FFE_RED: String = "#C8102E"
    const val SLATE: String = "#5C6779"

    /** The ground for a symbol key, resolved through the real library's series table (§10.3). */
    fun groundHex(symbolKey: String): String = when (seriesOf(symbolKey)) {
        SymbolLibrary.SERIES_FES -> FFE_RED
        SymbolLibrary.SERIES_LSS, SymbolLibrary.SERIES_MES, SymbolLibrary.SERIES_EES -> LSA_GREEN
        else -> SLATE
    }

    /**
     * Series lookup, guarded. The library is a large static table; if it ever fails to initialise
     * on a host without the Compose runtime, an export must still produce a readable plan rather
     * than blow up mid-render (§17.4).
     */
    private fun seriesOf(symbolKey: String): String =
        runCatching { SymbolLibrary.seriesOf(symbolKey) }.getOrDefault(SymbolLibrary.SERIES_APP)

    /** The fixed, semantic condition colours of §14 — identical to `ConditionColors`. */
    fun conditionHex(grade: ConditionGrade): String = when (grade) {
        ConditionGrade.GOOD -> "#1B873F"
        ConditionGrade.ACCEPTABLE -> "#6FA82C"
        ConditionGrade.MONITOR -> "#E8A317"
        ConditionGrade.DEFECTIVE -> "#E5661B"
        ConditionGrade.OUT_OF_SERVICE -> "#C2261B"
        ConditionGrade.NOT_CHECKED -> "#8A8F98"
    }
}
