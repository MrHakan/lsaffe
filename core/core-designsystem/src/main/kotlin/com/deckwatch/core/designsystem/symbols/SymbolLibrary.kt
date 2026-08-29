package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.deckwatch.core.designsystem.theme.Palette
import com.deckwatch.core.designsystem.theme.SignageColors

/**
 * The DeckWatch symbol library — §10.
 *
 * Keys are the canonical ones in `docs/SYMBOL_KEYS.md`, spelled exactly; the
 * series each key belongs to is taken from that document, **not** from the key
 * prefix (many `APP_*` keys are fire-fighting markers and take the red ground).
 *
 * Every pictogram is white on a transparent ground, so one vector serves the
 * standard ground and all six fire-control-plan media colours (§10.3).
 */
object SymbolLibrary {

    /** Returned by [get] for any key that is not in the library. */
    const val FALLBACK_KEY: String = "APP_GENERIC"

    const val SERIES_LSS: String = "LSS"
    const val SERIES_FES: String = "FES"
    const val SERIES_MES: String = "MES"
    const val SERIES_EES: String = "EES"
    const val SERIES_APP: String = "APP"

    /** Neutral ground for the app's own non-signage markers. */
    val GroundSlate: Color = Palette.Slate500

    /** Display order used by the picker and by grouped lists. */
    val seriesOrder: List<String> =
        listOf(SERIES_LSS, SERIES_FES, SERIES_MES, SERIES_EES, SERIES_APP)

    private class Entry(
        val key: String,
        val series: String,
        val tintable: Boolean,
        val provider: () -> ImageVector,
    )

    private val entries: List<Entry> = listOf(
        // ---- LSS — life-saving (green ground)
        Entry("LSS001", SERIES_LSS, false) { LssSymbols.LSS001 },
        Entry("LSS002", SERIES_LSS, false) { LssSymbols.LSS002 },
        Entry("LSS003", SERIES_LSS, false) { LssSymbols.LSS003 },
        Entry("LSS004", SERIES_LSS, false) { LssSymbols.LSS004 },
        Entry("LSS005", SERIES_LSS, false) { LssSymbols.LSS005 },
        Entry("LSS006", SERIES_LSS, false) { LssSymbols.LSS006 },
        Entry("LSS007", SERIES_LSS, false) { LssSymbols.LSS007 },
        Entry("LSS008", SERIES_LSS, false) { LssSymbols.LSS008 },
        Entry("LSS008_1", SERIES_LSS, false) { LssSymbols.LSS008_1 },
        Entry("LSS009", SERIES_LSS, false) { LssSymbols.LSS009 },
        Entry("LSS010", SERIES_LSS, false) { LssSymbols.LSS010 },
        Entry("LSS011", SERIES_LSS, false) { LssSymbols.LSS011 },
        Entry("LSS012", SERIES_LSS, false) { LssSymbols.LSS012 },
        Entry("LSS013", SERIES_LSS, false) { LssSymbols.LSS013 },
        Entry("LSS014", SERIES_LSS, false) { LssSymbols.LSS014 },
        Entry("LSS015", SERIES_LSS, false) { LssSymbols.LSS015 },
        Entry("LSS016", SERIES_LSS, false) { LssSymbols.LSS016 },
        Entry("LSS017", SERIES_LSS, false) { LssSymbols.LSS017 },
        Entry("LSS018", SERIES_LSS, false) { LssSymbols.LSS018 },
        Entry("LSS019", SERIES_LSS, false) { LssSymbols.LSS019 },
        Entry("LSS020", SERIES_LSS, false) { LssSymbols.LSS020 },
        Entry("LSS021", SERIES_LSS, false) { LssSymbols.LSS021 },
        Entry("LSS022", SERIES_LSS, false) { LssSymbols.LSS022 },
        // ---- FES — fire-fighting (red ground)
        Entry("FES001", SERIES_FES, true) { FesSymbols.FES001 },
        Entry("FES002", SERIES_FES, false) { FesSymbols.FES002 },
        Entry("FES003", SERIES_FES, false) { FesSymbols.FES003 },
        Entry("FES004", SERIES_FES, false) { FesSymbols.FES004 },
        Entry("FES005", SERIES_FES, true) { FesSymbols.FES005 },
        Entry("FES006", SERIES_FES, true) { FesSymbols.FES006 },
        Entry("FES007", SERIES_FES, false) { FesSymbols.FES007 },
        Entry("FES008", SERIES_FES, false) { FesSymbols.FES008 },
        Entry("FES009", SERIES_FES, true) { FesSymbols.FES009 },
        Entry("FES010", SERIES_FES, true) { FesSymbols.FES010 },
        Entry("FES011", SERIES_FES, true) { FesSymbols.FES011 },
        Entry("FES012", SERIES_FES, true) { FesSymbols.FES012 },
        // ---- APP — fire-fighting extensions (red ground)
        Entry("APP_FIRE_BLANKET", SERIES_FES, false) { AppFireSymbols.FIRE_BLANKET },
        Entry("APP_FIRE_HYDRANT", SERIES_FES, false) { AppFireSymbols.FIRE_HYDRANT },
        Entry("APP_FIRE_HOSE", SERIES_FES, false) { AppFireSymbols.FIRE_HOSE },
        Entry("APP_FIRE_NOZZLE", SERIES_FES, false) { AppFireSymbols.FIRE_NOZZLE },
        Entry("APP_FF_RADIO", SERIES_FES, false) { AppFireSymbols.FF_RADIO },
        Entry("APP_FIRE_ALARM_BELL", SERIES_FES, false) { AppFireSymbols.FIRE_ALARM_BELL },
        Entry("APP_FIRE_ALARM_LIGHT", SERIES_FES, false) { AppFireSymbols.FIRE_ALARM_LIGHT },
        Entry("APP_DETECTION_PANEL", SERIES_FES, false) { AppFireSymbols.DETECTION_PANEL },
        Entry("APP_SMOKE_DETECTOR", SERIES_FES, false) { AppFireSymbols.SMOKE_DETECTOR },
        Entry("APP_HEAT_DETECTOR", SERIES_FES, false) { AppFireSymbols.HEAT_DETECTOR },
        Entry("APP_FLAME_DETECTOR", SERIES_FES, false) { AppFireSymbols.FLAME_DETECTOR },
        Entry("APP_GAS_DETECTOR", SERIES_FES, false) { AppFireSymbols.GAS_DETECTOR },
        Entry("APP_FIRE_PUMP", SERIES_FES, false) { AppFireSymbols.FIRE_PUMP },
        Entry("APP_EMERGENCY_FIRE_PUMP", SERIES_FES, false) { AppFireSymbols.EMERGENCY_FIRE_PUMP },
        Entry("APP_ISC", SERIES_FES, false) { AppFireSymbols.ISC },
        Entry("APP_SECTION_VALVE", SERIES_FES, true) { AppFireSymbols.SECTION_VALVE },
        Entry("APP_SPRINKLER", SERIES_FES, true) { AppFireSymbols.SPRINKLER },
        Entry("APP_CO2_BANK", SERIES_FES, false) { AppFireSymbols.CO2_BANK },
        Entry("APP_FOAM_SYSTEM", SERIES_FES, false) { AppFireSymbols.FOAM_SYSTEM },
        Entry("APP_INERT_GAS", SERIES_FES, false) { AppFireSymbols.INERT_GAS },
        Entry("APP_GALLEY_HOOD", SERIES_FES, false) { AppFireSymbols.GALLEY_HOOD },
        Entry("APP_FIRE_DOOR", SERIES_FES, false) { AppFireSymbols.FIRE_DOOR },
        Entry("APP_FIRE_DAMPER", SERIES_FES, false) { AppFireSymbols.FIRE_DAMPER },
        Entry("APP_VENT_STOP", SERIES_FES, false) { AppFireSymbols.VENT_STOP },
        Entry("APP_QUICK_CLOSING_VALVE", SERIES_FES, false) { AppFireSymbols.QUICK_CLOSING_VALVE },
        Entry("APP_SCBA", SERIES_FES, false) { AppFireSymbols.SCBA },
        Entry("APP_FIREMANS_OUTFIT", SERIES_FES, false) { AppFireSymbols.FIREMANS_OUTFIT },
        Entry("APP_SAFETY_LAMP", SERIES_FES, false) { AppFireSymbols.SAFETY_LAMP },
        Entry("APP_FIRE_AXE", SERIES_FES, false) { AppFireSymbols.FIRE_AXE },
        // ---- MES — escape (green ground)
        Entry("MES001", SERIES_MES, false) { MesSymbols.MES001 },
        Entry("MES002", SERIES_MES, false) { MesSymbols.MES002 },
        Entry("MES003", SERIES_MES, false) { MesSymbols.MES003 },
        Entry("APP_ARROW", SERIES_MES, false) { MesSymbols.ARROW },
        Entry("APP_LLL", SERIES_MES, false) { MesSymbols.LLL },
        Entry("APP_ESCAPE_TRUNK", SERIES_MES, false) { MesSymbols.ESCAPE_TRUNK },
        // ---- EES — emergency equipment (green ground)
        Entry("EES001", SERIES_EES, false) { EesSymbols.EES001 },
        Entry("EES002", SERIES_EES, false) { EesSymbols.EES002 },
        Entry("EES003", SERIES_EES, false) { EesSymbols.EES003 },
        Entry("EES004", SERIES_EES, false) { EesSymbols.EES004 },
        Entry("EES005", SERIES_EES, false) { EesSymbols.EES005 },
        Entry("EES006", SERIES_EES, false) { EesSymbols.EES006 },
        Entry("EES007", SERIES_EES, false) { EesSymbols.EES007 },
        Entry("EES008", SERIES_EES, false) { EesSymbols.EES008 },
        Entry("EES009", SERIES_EES, false) { EesSymbols.EES009 },
        Entry("EES010", SERIES_EES, false) { EesSymbols.EES010 },
        Entry("EES012", SERIES_EES, false) { EesSymbols.EES012 },
        Entry("EES013", SERIES_EES, false) { EesSymbols.EES013 },
        // ---- APP — machinery, controls, documents, LSA components (slate ground)
        Entry("APP_EMERGENCY_GENERATOR", SERIES_APP, false) {
            AppMiscSymbols.EMERGENCY_GENERATOR
        },
        Entry("APP_EMERGENCY_SWITCHBOARD", SERIES_APP, false) {
            AppMiscSymbols.EMERGENCY_SWITCHBOARD
        },
        Entry("APP_BATTERY", SERIES_APP, false) { AppMiscSymbols.BATTERY },
        Entry("APP_WATERTIGHT_DOOR", SERIES_APP, false) { AppMiscSymbols.WATERTIGHT_DOOR },
        Entry("APP_SKYLIGHT", SERIES_APP, false) { AppMiscSymbols.SKYLIGHT },
        Entry("APP_FIRE_CONTROL_PLAN", SERIES_APP, false) { AppMiscSymbols.FIRE_CONTROL_PLAN },
        Entry("APP_MUSTER_LIST", SERIES_APP, false) { AppMiscSymbols.MUSTER_LIST },
        Entry("APP_DOCUMENT", SERIES_APP, false) { AppMiscSymbols.DOCUMENT },
        Entry("APP_SOPEP", SERIES_APP, false) { AppMiscSymbols.SOPEP },
        Entry("APP_HRU", SERIES_APP, false) { AppMiscSymbols.HRU },
        Entry("APP_PILOT_LADDER", SERIES_APP, false) { AppMiscSymbols.PILOT_LADDER },
        Entry("APP_DAVIT", SERIES_APP, false) { AppMiscSymbols.DAVIT },
        Entry("APP_WINCH", SERIES_APP, false) { AppMiscSymbols.WINCH },
        Entry("APP_RELEASE_GEAR", SERIES_APP, false) { AppMiscSymbols.RELEASE_GEAR },
        Entry("APP_FALLS", SERIES_APP, false) { AppMiscSymbols.FALLS },
        Entry("APP_ENGINE", SERIES_APP, false) { AppMiscSymbols.ENGINE },
        Entry(FALLBACK_KEY, SERIES_APP, false) { AppMiscSymbols.GENERIC },
    )

    private val byKey: Map<String, Entry> = entries.associateBy { it.key }

    private val fallback: Entry = byKey.getValue(FALLBACK_KEY)

    /** Every canonical key, in `docs/SYMBOL_KEYS.md` order. */
    val keys: List<String> = entries.map { it.key }

    /**
     * Every symbol in the library. Built on first access; individual vectors
     * are also lazy, so [get] on its own never builds the whole set.
     */
    val all: Map<String, ImageVector> by lazy {
        entries.associate { it.key to it.provider() }
    }

    /** The pictogram for [key], falling back to [FALLBACK_KEY]. */
    fun get(key: String): ImageVector = (byKey[key] ?: fallback).provider()

    /** True when [key] is one of the library's canonical keys. */
    fun contains(key: String): Boolean = byKey.containsKey(key)

    /** `"LSS"` / `"FES"` / `"MES"` / `"EES"` / `"APP"` — from `SYMBOL_KEYS.md`. */
    fun seriesOf(key: String): String = (byKey[key] ?: fallback).series

    /** Keys of the series, in library order — used to group the picker. */
    fun keysOf(series: String): List<String> =
        entries.filter { it.series == series }.map { it.key }

    /** The standard sign ground for [key] — ISO 3864-1 via §10.1. */
    fun groundColor(key: String): Color = when (seriesOf(key)) {
        SERIES_FES -> SignageColors.FfeRed
        SERIES_LSS, SERIES_MES, SERIES_EES -> SignageColors.LsaGreen
        else -> GroundSlate
    }

    /** Symbols that may be re-tinted with a fire-control-plan media colour. */
    fun tintableKeys(): Set<String> = tintable

    /** True when [key] carries a media colour code (§10.1, §10.3). */
    fun isTintable(key: String): Boolean = key in tintable

    private val tintable: Set<String> =
        entries.filter { it.tintable }.map { it.key }.toSet()

    /**
     * The pictogram colour to use on [ground]. White everywhere except on the
     * pale media colours (powder white, foam yellow), where white on white
     * would vanish.
     */
    fun pictogramColorOn(ground: Color): Color =
        if (ground.luminance() > PALE_GROUND_LUMINANCE) Palette.Navy900 else Color.White

    private const val PALE_GROUND_LUMINANCE = 0.45f
}

/** Fire-control-plan media colour code — §10.1, §10.3. */
enum class MediaColor(val color: Color) {
    CO2(SignageColors.MediaCo2Grey),
    OTHER_GAS(SignageColors.MediaOtherGasBrown),
    POWDER(SignageColors.MediaPowderWhite),
    FOAM(SignageColors.MediaFoamYellow),
    WATER(SignageColors.MediaWaterGreen),
    SPRINKLER(SignageColors.MediaSprinklerOrange),
}
