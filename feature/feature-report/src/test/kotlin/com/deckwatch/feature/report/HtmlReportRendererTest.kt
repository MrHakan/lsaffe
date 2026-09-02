package com.deckwatch.feature.report

import com.deckwatch.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The rendered document of §13.2 and §13.4. */
class HtmlReportRendererTest {

    private val renderer = HtmlReportRenderer(groundHex = { "#123456" })

    private fun render(
        scope: ExportScope = ExportScope.FULL_BACKUP,
        payload: DeckWatchExportPayload = ReportFixtures.payload(scope),
        options: ExportOptions = ExportOptions(scope = scope),
        photos: Map<String, String> = emptyMap(),
    ): String = renderer.render(
        ReportDocument(payload = payload, options = options, photos = photos),
    )

    @Test
    fun `every report carries the disclaimer verbatim`() {
        for (scope in ExportScope.entries) {
            val html = render(scope)
            assertThat(html).contains(REPORT_DISCLAIMER_ANCHOR)
            // The whole paragraph, HTML-escaped exactly as the renderer writes it.
            assertThat(html).contains("The Master&#39;s and the Company&#39;s responsibilities")
            assertThat(html).contains("statutory obligation")
        }
    }

    @Test
    fun `the document is one self-contained file with no external reference`() {
        val html = render()
        assertThat(html).startsWith("<!doctype html>")
        assertThat(html).contains("<style>")
        assertThat(html).contains("<div id=\"report\">")
        assertThat(html).contains("id=\"deckwatch-data\"")
        assertThat(html).doesNotContain("https://")
        assertThat(html).doesNotContain("<link ")
        assertThat(html).doesNotContain("fetch(")
        assertThat(html).doesNotContain("src=\"http")
    }

    @Test
    fun `the header block states vessel, IMO, flag, class, type, timestamp and app version`() {
        val html = render()
        assertThat(html).contains("MV Example")
        assertThat(html).contains("9074729")
        assertThat(html).contains("MARSHALL_ISLANDS")
        assertThat(html).contains("DNV")
        assertThat(html).contains("1.0.0 (1)")
        assertThat(html).contains("Full vessel backup")
    }

    @Test
    fun `the summary strip counts by condition and by due status`() {
        val html = render()
        // Three live items: GOOD, MONITOR, NOT_CHECKED — and one OVERDUE instance.
        assertThat(html).contains(">Good</div>")
        assertThat(html).contains(">Monitor</div>")
        assertThat(html).contains(">Overdue</div>")
    }

    @Test
    fun `user text that closes a script tag cannot escape the data block`() {
        val hostile = ReportFixtures.note.copy(
            title = "</script><script>alert('x')</script>",
            body = "Also a comment opener: <!-- and an ampersand & a quote \"",
        )
        val html = render(payload = ReportFixtures.payload(notes = listOf(hostile)))

        // The static report escapes it as text...
        assertThat(staticPart(html)).doesNotContain("<script>alert(")
        assertThat(staticPart(html)).contains("&lt;/script&gt;")
        // ...and the data block carries no tag-like sequence at all, so nothing can close it early.
        assertThat(dataBlock(html)).doesNotContain("<")
        assertThat(dataBlock(html)).contains("\\u003c/script")
        // Exactly one </script> closes the data block, and one more closes the interactive layer.
        assertThat(html.occurrencesOf("</script>")).isEqualTo(2)

        // And the block still parses, which is the point of the escape being lossless.
        val json = dataBlock(html)
        val parsed = PayloadParser.parseJson(HtmlEscape.unescapeJsonFromScriptBlock(json))
        assertThat(parsed).isInstanceOf(PayloadParseResult.Parsed::class.java)
        val payload = (parsed as PayloadParseResult.Parsed).payload
        assertThat(payload.userNotes.single().title).isEqualTo("</script><script>alert('x')</script>")
    }

    @Test
    fun `the deck sheet draws one marker per item on that deck plus a legend row each`() {
        val html = render(
            scope = ExportScope.DECK_SHEET,
            options = ExportOptions(scope = ExportScope.DECK_SHEET, deckId = ReportFixtures.DECK_ID),
        )
        assertThat(html).contains("<svg class=\"deckplan\"")
        // Two items sit on the deck; the third is unplaced and must not gain a marker.
        assertThat(html.occurrencesOf("class=\"marker\"")).isEqualTo(2)
        assertThat(html).contains("data-tag=\"FE-UD-01\"")
        assertThat(html).contains("data-tag=\"LB-UD-02\"")
        assertThat(html).doesNotContain("data-tag=\"FE-STORE-09\"")
        // Numbered 1..n, matching the legend order.
        assertThat(html).contains("text-anchor=\"middle\">1</text>")
        assertThat(html).contains("text-anchor=\"middle\">2</text>")
        assertThat(html).contains("Legend")
    }

    @Test
    fun `the round report carries a signature block`() {
        val html = render(
            scope = ExportScope.ROUND_REPORT,
            options = ExportOptions(scope = ExportScope.ROUND_REPORT, roundId = ReportFixtures.ROUND_ID),
        )
        assertThat(html).contains("Inspected by")
        assertThat(html).contains("Verified by")
        assertThat(html).contains("signature")
        assertThat(html).contains("Seal intact, gauge in the green.")
    }

    @Test
    fun `the due list scope prints the Due tab snapshot`() {
        val request = ReportFixtures.dueRequest()
        val html = render(
            scope = ExportScope.DUE_LIST,
            payload = ReportFixtures.payload(scope = ExportScope.DUE_LIST, dueList = request),
            options = ExportOptions(scope = ExportScope.DUE_LIST),
        )
        assertThat(html).contains("Monthly inspection")
        assertThat(html).contains("-12")
        assertThat(html).contains("Annual service, &quot;thorough&quot;")
    }

    @Test
    fun `the print stylesheet sets A4, repeats table headers and keeps grounds light`() {
        val html = render()
        assertThat(html).contains("@page { size: A4;")
        assertThat(html).contains("display: table-header-group")
        assertThat(html).contains("page-break")
        assertThat(html).contains("body { background: #ffffff; }")
    }

    @Test
    fun `a photo that could not be embedded becomes a visible placeholder, not a broken image`() {
        val withPhotos = ReportFixtures.deficiency.copy(photoUris = listOf("file:///missing.jpg"))
        val payload = ReportFixtures.payload().copy(deficiencies = listOf(withPhotos))
        val html = renderer.render(
            ReportDocument(
                payload = payload,
                options = ExportOptions(
                    scope = ExportScope.DEFICIENCY_REPORT,
                    photoTier = PhotoTier.DEFICIENCY_ONLY,
                ),
                photos = emptyMap(),
            ),
        )
        assertThat(html).contains("photo-missing")
        assertThat(html).doesNotContain("<img alt=\"\" src=\"file")
    }

    @Test
    fun `an embedded photo travels as a data URI`() {
        val withPhotos = ReportFixtures.deficiency.copy(photoUris = listOf("file:///a.jpg"))
        val payload = ReportFixtures.payload().copy(deficiencies = listOf(withPhotos))
        val html = renderer.render(
            ReportDocument(
                payload = payload,
                options = ExportOptions(
                    scope = ExportScope.DEFICIENCY_REPORT,
                    photoTier = PhotoTier.DEFICIENCY_ONLY,
                ),
                photos = mapOf("file:///a.jpg" to "data:image/jpeg;base64,QUJD"),
            ),
        )
        assertThat(html).contains("src=\"data:image/jpeg;base64,QUJD\"")
    }

    @Test
    fun `the PSC pack keeps only the last twelve months of rounds`() {
        val old = ReportFixtures.round.copy(
            id = "round-old",
            title = "Two years ago",
            startedAt = TestData.referenceMillis - 2L * 365 * 24 * 60 * 60 * 1000,
        )
        val payload = ReportFixtures.payload().copy(rounds = listOf(ReportFixtures.round, old))
        val html = renderer.render(
            ReportDocument(
                payload = payload,
                options = ExportOptions(scope = ExportScope.PSC_SURVEY_PACK),
            ),
        )
        // The payload still carries both rounds — the narrowing that drops the old one for a PSC
        // pack is the assembler's job; what the renderer guarantees is what gets *printed*.
        assertThat(staticPart(html)).contains("Weekly LSA round")
        assertThat(staticPart(html)).doesNotContain("Two years ago")
        assertThat(staticPart(html)).contains("Certificate status")
    }

    @Test
    fun `an empty payload still renders a complete, readable document`() {
        val html = renderer.render(ReportDocument(payload = DeckWatchExportPayload()))
        assertThat(html).contains(REPORT_DISCLAIMER_ANCHOR)
        assertThat(html).endsWith("</html>\n")
    }

    private fun String.occurrencesOf(needle: String): Int = split(needle).size - 1

    /** The static `<div id="report">` — what a reader with JavaScript disabled sees. */
    private fun staticPart(html: String): String =
        html.substringBefore("<script id=\"deckwatch-data\"")

    /** The raw contents of the `deckwatch-data` block. */
    private fun dataBlock(html: String): String =
        html.substringAfter("type=\"application/json\">").substringBefore("</script>")
}
