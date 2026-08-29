package com.deckwatch.feature.inspection

import com.deckwatch.core.model.EquipmentGroup
import com.deckwatch.core.model.PerformedBy
import com.deckwatch.core.model.TaskStatus
import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** The clipboard export of §12 and the payload `feature-report` will consume — §13.3. */
class DueExportTest {

    private val today = TestData.referenceDay

    private val labels = DueExportLabels(
        header = "DeckWatch — Due list",
        vesselLabel = "Vessel",
        segmentLabel = "Segment",
        segmentName = "Overdue",
        filtersLabel = "Filters",
        filtersNone = "None",
        generatedLabel = "Generated",
        surveyLabel = "SEC expiry",
        columnTag = "TAG",
        columnDeck = "DECK",
        columnTask = "TASK",
        columnDue = "DUE",
        columnDays = "DAYS",
        columnBy = "BY WHOM",
        totalLabel = "Total",
        emptyLabel = "Nothing to list.",
        performedByNames = mapOf(
            PerformedBy.SHIP_STAFF to "Ship's staff",
            PerformedBy.AUTHORISED_SERVICE_PROVIDER to "Authorised service provider",
        ),
    )

    private fun request(
        lines: List<DueExportLine> = defaultLines,
        filters: DueExportFilters = DueExportFilters(),
        surveyCertExpiry: Long? = null,
    ) = DueExportRequest(
        vesselName = "MV Example",
        vesselImoNumber = "9074729",
        segment = DueSegment.OVERDUE,
        generatedOnEpochDay = today,
        filters = filters,
        lines = lines,
        surveyCertExpiry = surveyCertExpiry,
    )

    private val defaultLines = listOf(
        DueExportLine(
            tag = "FE-UD-01",
            task = "Portable fire extinguisher — monthly check",
            dueDate = today - 12,
            dayDelta = -12,
            performedBy = PerformedBy.SHIP_STAFF,
            deck = "UD",
            status = TaskStatus.OVERDUE,
        ),
        DueExportLine(
            tag = "LB-01",
            task = "Lifeboat — annual thorough examination",
            dueDate = today + 30,
            dayDelta = 30,
            performedBy = PerformedBy.AUTHORISED_SERVICE_PROVIDER,
            deck = "BD",
            status = TaskStatus.PENDING,
        ),
    )

    @Test
    fun `the header names the vessel, the segment and the day it was taken`() {
        val text = renderDueListText(request(), labels)
        assertThat(text).contains("DeckWatch — Due list")
        assertThat(text).contains("Vessel: MV Example (IMO 9074729)")
        assertThat(text).contains("Segment: Overdue")
        assertThat(text).contains("Filters: None")
        assertThat(text).contains("Generated: 2026-01-01")
    }

    @Test
    fun `every row appears with its tag, deck, due date, signed delta and performer`() {
        val text = renderDueListText(request(), labels)
        assertThat(text).contains("FE-UD-01")
        assertThat(text).contains("Portable fire extinguisher — monthly check")
        assertThat(text).contains("2025-12-20")
        assertThat(text).contains("-12")
        assertThat(text).contains("Ship's staff")
        assertThat(text).contains("+30")
        assertThat(text).contains("Authorised service provider")
        assertThat(text).contains("Total: 2")
    }

    @Test
    fun `columns are padded so the table survives a paste into a message`() {
        val lines = renderDueListText(request(), labels).lines()
        val heading = lines.first { it.startsWith("TAG") }
        val rule = lines[lines.indexOf(heading) + 1]
        val firstRow = lines[lines.indexOf(heading) + 2]
        assertThat(rule).isEqualTo("-".repeat(heading.length))
        // The tag column is as wide as its widest value, so every column starts at one offset.
        val deckColumn = heading.indexOf("DECK")
        assertThat(firstRow.substring(0, "FE-UD-01".length)).isEqualTo("FE-UD-01")
        assertThat(firstRow.substring(deckColumn, deckColumn + 2)).isEqualTo("UD")
    }

    @Test
    fun `active filters are named in the header`() {
        val text = renderDueListText(
            request(
                filters = DueExportFilters(
                    deckName = "UD",
                    group = EquipmentGroup.FFE,
                    performedBy = PerformedBy.SHIP_STAFF,
                ),
            ),
            labels,
        )
        assertThat(text).contains("Filters: UD · FFE · Ship's staff")
    }

    @Test
    fun `survey prep exports state the certificate expiry`() {
        val text = renderDueListText(request(surveyCertExpiry = today + 100), labels)
        assertThat(text).contains("SEC expiry: 2026-04-11")
    }

    @Test
    fun `an empty list still produces a readable sheet`() {
        val text = renderDueListText(request(lines = emptyList()), labels)
        assertThat(text).contains("Nothing to list.")
        assertThat(text).doesNotContain("TAG")
    }

    @Test
    fun `the delta reads with an explicit sign`() {
        assertThat(formatDelta(-5)).isEqualTo("-5")
        assertThat(formatDelta(0)).isEqualTo("0")
        assertThat(formatDelta(12)).isEqualTo("+12")
    }

    @Test
    fun `the request round-trips through JSON for feature-report`() {
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(DueExportRequest.serializer(), request())
        val decoded = json.decodeFromString(DueExportRequest.serializer(), encoded)
        assertThat(decoded).isEqualTo(request())
    }
}
