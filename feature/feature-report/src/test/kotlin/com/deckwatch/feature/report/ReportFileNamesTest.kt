package com.deckwatch.feature.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The §13.6 filename and its sanitisation. */
class ReportFileNamesTest {

    private val atMillis = LocalDateTime.of(2026, 3, 12, 7, 5)
        .toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun name(vesselName: String?, scope: ExportScope = ExportScope.FULL_BACKUP) =
        ReportFileNames.forReport(vesselName, scope, atMillis, zone = ZoneOffset.UTC)

    @Test
    fun `the pattern is DeckWatch_VESSEL_TYPE_timestamp`() {
        assertThat(name("MV Example")).isEqualTo("DeckWatch_MV_EXAMPLE_FULL_20260312_0705.html")
    }

    @Test
    fun `each scope has its own slug`() {
        assertThat(name("MV Example", ExportScope.DUE_LIST)).contains("_DUE_")
        assertThat(name("MV Example", ExportScope.ROUND_REPORT)).contains("_ROUND_")
        assertThat(name("MV Example", ExportScope.DEFICIENCY_REPORT)).contains("_DEFICIENCIES_")
        assertThat(name("MV Example", ExportScope.DECK_SHEET)).contains("_DECK_")
        assertThat(name("MV Example", ExportScope.PSC_SURVEY_PACK)).contains("_PSC_")
    }

    @Test
    fun `path separators and shell characters cannot reach the filename`() {
        val sanitised = ReportFileNames.sanitiseVesselName("""M/V "Star" \ *?<>|:""")
        assertThat(sanitised).isEqualTo("M_V_STAR")
        assertThat(name("""../../etc/passwd""")).doesNotContain("..")
        assertThat(name("""../../etc/passwd""")).doesNotContain("/")
    }

    @Test
    fun `Turkish letters are transliterated rather than blanked out`() {
        assertThat(ReportFileNames.sanitiseVesselName("Gümüş Şafak")).isEqualTo("GUMUS_SAFAK")
        assertThat(ReportFileNames.sanitiseVesselName("Çağrı")).isEqualTo("CAGRI")
    }

    @Test
    fun `runs of punctuation collapse to a single underscore and the ends are trimmed`() {
        assertThat(ReportFileNames.sanitiseVesselName("  --MV--  Example--  ")).isEqualTo("MV_EXAMPLE")
    }

    @Test
    fun `a nameless vessel still produces a usable filename`() {
        assertThat(ReportFileNames.sanitiseVesselName(null)).isEqualTo(ReportFileNames.FALLBACK_VESSEL)
        assertThat(ReportFileNames.sanitiseVesselName("!!!")).isEqualTo(ReportFileNames.FALLBACK_VESSEL)
        assertThat(name(null)).startsWith("DeckWatch_VESSEL_")
    }

    @Test
    fun `a reserved DOS device name is escaped`() {
        assertThat(ReportFileNames.sanitiseVesselName("con")).isEqualTo("CON_")
        assertThat(ReportFileNames.sanitiseVesselName("lpt1")).isEqualTo("LPT1_")
    }

    @Test
    fun `a very long name is truncated`() {
        val long = "A".repeat(200)
        assertThat(ReportFileNames.sanitiseVesselName(long))
            .hasLength(ReportFileNames.MAX_VESSEL_CHARS)
    }

    @Test
    fun `the extension follows the format asked for`() {
        val json = ReportFileNames.forReport(
            "MV Example",
            ExportScope.FULL_BACKUP,
            atMillis,
            extension = ReportFileNames.JSON_EXTENSION,
            zone = ZoneOffset.UTC,
        )
        assertThat(json).endsWith(".json")
    }
}
