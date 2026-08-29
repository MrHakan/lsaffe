package com.deckwatch.core.designsystem.symbols

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.deckwatch.core.designsystem.theme.SignageColors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards `docs/SYMBOL_KEYS.md` against the library: every canonical key is
 * implemented, nothing extra is shipped, and every pictogram obeys the house
 * rules that make one vector serve every ground colour (§10.3).
 *
 * [CANONICAL_KEYS] is transcribed from the document, in document order.
 */
class SymbolLibraryTest {

    @Test
    fun `every canonical key is implemented`() {
        assertThat(SymbolLibrary.keys).containsExactlyElementsIn(CANONICAL_KEYS).inOrder()
    }

    @Test
    fun `the map exposes exactly the canonical keys`() {
        assertThat(SymbolLibrary.all.keys).containsExactlyElementsIn(CANONICAL_KEYS)
        assertThat(SymbolLibrary.all).hasSize(CANONICAL_KEYS.size)
    }

    @Test
    fun `unknown keys fall back to the generic marker`() {
        assertThat(SymbolLibrary.get("NOT_A_KEY"))
            .isSameInstanceAs(SymbolLibrary.get(SymbolLibrary.FALLBACK_KEY))
        assertThat(SymbolLibrary.contains("NOT_A_KEY")).isFalse()
        assertThat(SymbolLibrary.seriesOf("NOT_A_KEY")).isEqualTo(SymbolLibrary.SERIES_APP)
    }

    @Test
    fun `series come from the key list, not from the key prefix`() {
        assertThat(SymbolLibrary.seriesOf("LSS001")).isEqualTo(SymbolLibrary.SERIES_LSS)
        assertThat(SymbolLibrary.seriesOf("FES001")).isEqualTo(SymbolLibrary.SERIES_FES)
        // APP_* keys listed under the FES sections stay in the FES series.
        assertThat(SymbolLibrary.seriesOf("APP_FIRE_HYDRANT")).isEqualTo(SymbolLibrary.SERIES_FES)
        assertThat(SymbolLibrary.seriesOf("APP_SCBA")).isEqualTo(SymbolLibrary.SERIES_FES)
        assertThat(SymbolLibrary.seriesOf("APP_ARROW")).isEqualTo(SymbolLibrary.SERIES_MES)
        assertThat(SymbolLibrary.seriesOf("EES008")).isEqualTo(SymbolLibrary.SERIES_EES)
        assertThat(SymbolLibrary.seriesOf("APP_HRU")).isEqualTo(SymbolLibrary.SERIES_APP)
    }

    @Test
    fun `ground colours follow the sign convention`() {
        assertThat(SymbolLibrary.groundColor("LSS005")).isEqualTo(SignageColors.LsaGreen)
        assertThat(SymbolLibrary.groundColor("MES001")).isEqualTo(SignageColors.LsaGreen)
        assertThat(SymbolLibrary.groundColor("EES001")).isEqualTo(SignageColors.LsaGreen)
        assertThat(SymbolLibrary.groundColor("FES001")).isEqualTo(SignageColors.FfeRed)
        assertThat(SymbolLibrary.groundColor("APP_FIRE_AXE")).isEqualTo(SignageColors.FfeRed)
        assertThat(SymbolLibrary.groundColor("APP_WINCH")).isEqualTo(SymbolLibrary.GroundSlate)
        assertThat(SymbolLibrary.groundColor("NOT_A_KEY")).isEqualTo(SymbolLibrary.GroundSlate)
    }

    @Test
    fun `media-tintable keys match the key list`() {
        assertThat(SymbolLibrary.tintableKeys()).containsExactlyElementsIn(TINTABLE_KEYS)
        TINTABLE_KEYS.forEach { assertThat(SymbolLibrary.isTintable(it)).isTrue() }
        assertThat(SymbolLibrary.isTintable("LSS001")).isFalse()
    }

    @Test
    fun `media colours cover the fire control plan code`() {
        assertThat(MediaColor.entries.map { it.color }).containsExactly(
            SignageColors.MediaCo2Grey,
            SignageColors.MediaOtherGasBrown,
            SignageColors.MediaPowderWhite,
            SignageColors.MediaFoamYellow,
            SignageColors.MediaWaterGreen,
            SignageColors.MediaSprinklerOrange,
        )
    }

    @Test
    fun `pale media grounds get a dark pictogram`() {
        assertThat(SymbolLibrary.pictogramColorOn(SignageColors.FfeRed)).isEqualTo(Color.White)
        assertThat(SymbolLibrary.pictogramColorOn(SignageColors.LsaGreen)).isEqualTo(Color.White)
        assertThat(SymbolLibrary.pictogramColorOn(SignageColors.MediaPowderWhite))
            .isNotEqualTo(Color.White)
    }

    @Test
    fun `every pictogram is a 24dp white fill-only vector`() {
        SymbolLibrary.all.forEach { (key, vector) ->
            assertThat(vector.viewportWidth).isEqualTo(24f)
            assertThat(vector.viewportHeight).isEqualTo(24f)
            assertThat(vector.defaultWidth).isEqualTo(24.dp)
            assertThat(vector.defaultHeight).isEqualTo(24.dp)

            val paths = vector.root.flatten()
            assertThat(paths).isNotEmpty()
            paths.forEach { path ->
                assertThat(path.stroke).isNull()
                assertThat((path.fill as? SolidColor)?.value).isEqualTo(Color.White)
                assertThat(path.pathData).isNotEmpty()
            }
            // The key is carried on the vector so exports can name it.
            assertThat(vector.name).isNotEmpty()
            assertThat(key).isNotEmpty()
        }
    }

    private fun VectorGroup.flatten(): List<VectorPath> {
        val out = mutableListOf<VectorPath>()
        forEach { node: VectorNode ->
            when (node) {
                is VectorPath -> out += node
                is VectorGroup -> out += node.flatten()
            }
        }
        return out
    }

    private companion object {
        /** Transcribed from `docs/SYMBOL_KEYS.md`, in document order. */
        val CANONICAL_KEYS = listOf(
            // LSS — life-saving (green ground)
            "LSS001", "LSS002", "LSS003", "LSS004", "LSS005", "LSS006", "LSS007",
            "LSS008", "LSS008_1", "LSS009", "LSS010", "LSS011", "LSS012", "LSS013",
            "LSS014", "LSS015", "LSS016", "LSS017", "LSS018", "LSS019", "LSS020",
            "LSS021", "LSS022",
            // FES — fire-fighting (red ground)
            "FES001", "FES002", "FES003", "FES004", "FES005", "FES006",
            "FES007", "FES008", "FES009", "FES010", "FES011", "FES012",
            // APP — fire-fighting extensions (red ground)
            "APP_FIRE_BLANKET", "APP_FIRE_HYDRANT", "APP_FIRE_HOSE", "APP_FIRE_NOZZLE",
            "APP_FF_RADIO", "APP_FIRE_ALARM_BELL", "APP_FIRE_ALARM_LIGHT",
            "APP_DETECTION_PANEL", "APP_SMOKE_DETECTOR", "APP_HEAT_DETECTOR",
            "APP_FLAME_DETECTOR", "APP_GAS_DETECTOR", "APP_FIRE_PUMP",
            "APP_EMERGENCY_FIRE_PUMP", "APP_ISC", "APP_SECTION_VALVE", "APP_SPRINKLER",
            "APP_CO2_BANK", "APP_FOAM_SYSTEM", "APP_INERT_GAS", "APP_GALLEY_HOOD",
            "APP_FIRE_DOOR", "APP_FIRE_DAMPER", "APP_VENT_STOP", "APP_QUICK_CLOSING_VALVE",
            "APP_SCBA", "APP_FIREMANS_OUTFIT", "APP_SAFETY_LAMP", "APP_FIRE_AXE",
            // MES — escape (green ground)
            "MES001", "MES002", "MES003", "APP_ARROW", "APP_LLL", "APP_ESCAPE_TRUNK",
            // EES — emergency equipment (green ground)
            "EES001", "EES002", "EES003", "EES004", "EES005", "EES006", "EES007",
            "EES008", "EES009", "EES010", "EES012", "EES013",
            // APP — machinery, controls, documents, LSA components (slate ground)
            "APP_EMERGENCY_GENERATOR", "APP_EMERGENCY_SWITCHBOARD", "APP_BATTERY",
            "APP_WATERTIGHT_DOOR", "APP_SKYLIGHT", "APP_FIRE_CONTROL_PLAN",
            "APP_MUSTER_LIST", "APP_DOCUMENT", "APP_SOPEP", "APP_HRU", "APP_PILOT_LADDER",
            "APP_DAVIT", "APP_WINCH", "APP_RELEASE_GEAR", "APP_FALLS", "APP_ENGINE",
            "APP_GENERIC",
        )

        /** The `mediaTintable = yes` rows of `docs/SYMBOL_KEYS.md`. */
        val TINTABLE_KEYS = listOf(
            "FES001", "FES005", "FES006", "FES009", "FES010", "FES011", "FES012",
            "APP_SECTION_VALVE", "APP_SPRINKLER",
        )
    }
}
