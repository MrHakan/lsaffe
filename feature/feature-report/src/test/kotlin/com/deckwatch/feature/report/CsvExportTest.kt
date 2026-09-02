package com.deckwatch.feature.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** RFC 4180 quoting and the two CSV documents of §13.5. */
class CsvExportTest {

    @Test
    fun `a plain field is not quoted`() {
        assertThat(CsvExport.field("FE-UD-07")).isEqualTo("FE-UD-07")
    }

    @Test
    fun `a field containing a comma is quoted`() {
        assertThat(CsvExport.field("Stbd side, aft")).isEqualTo("\"Stbd side, aft\"")
    }

    @Test
    fun `an embedded quote is doubled inside quotes`() {
        assertThat(CsvExport.field("""6" hose""")).isEqualTo(""""6"" hose"""")
    }

    @Test
    fun `a field with a newline is quoted so the record survives`() {
        assertThat(CsvExport.field("line one\nline two")).isEqualTo("\"line one\nline two\"")
        assertThat(CsvExport.field("carriage\rreturn")).isEqualTo("\"carriage\rreturn\"")
    }

    @Test
    fun `leading and trailing spaces are preserved by quoting`() {
        assertThat(CsvExport.field(" FE-UD-07 ")).isEqualTo("\" FE-UD-07 \"")
    }

    @Test
    fun `a null field becomes an empty, unquoted field`() {
        assertThat(CsvExport.field(null)).isEmpty()
    }

    @Test
    fun `records are separated by CRLF and the file carries a UTF-8 BOM for Excel`() {
        val document = CsvExport.document(listOf("a", "b"), listOf(listOf("1", "2")))
        assertThat(document).startsWith(CsvExport.UTF8_BOM)
        assertThat(document).contains("a,b${CsvExport.CRLF}1,2")
        assertThat(document).endsWith(CsvExport.CRLF)
    }

    @Test
    fun `the equipment register lists live items only, sorted by tag`() {
        val deleted = ReportFixtures.unplaced.copy(deletedAt = 1L)
        val payload = ReportFixtures.payload(
            equipment = listOf(ReportFixtures.lifebuoy, ReportFixtures.extinguisher, deleted),
        )
        val csv = CsvExport.equipmentRegister(payload, typeNames = mapOf("LSA_LIFEBUOY" to "Lifebuoy"))
        val rows = csv.trim().lines()

        assertThat(rows).hasSize(3) // header + two live items
        assertThat(rows[1]).startsWith("FE-UD-01")
        assertThat(rows[2]).startsWith("LB-UD-02")
        assertThat(csv).contains("Lifebuoy")
        assertThat(csv).contains("\"Stbd side, aft of provision crane\"")
        assertThat(csv).doesNotContain("FE-STORE-09")
    }

    @Test
    fun `the due list CSV quotes a task title that contains a quote`() {
        val csv = CsvExport.dueList(ReportFixtures.dueRequest())
        assertThat(csv).contains("\"Annual service, \"\"thorough\"\"\"")
        assertThat(csv).contains("-12")
        assertThat(csv).contains("+30")
    }
}
