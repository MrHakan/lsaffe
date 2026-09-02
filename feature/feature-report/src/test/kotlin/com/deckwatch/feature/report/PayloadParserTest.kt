package com.deckwatch.feature.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Reading a file back — §13.5, and the "survives a corrupted or truncated file" of §17.4. */
class PayloadParserTest {

    private val renderer = HtmlReportRenderer(groundHex = { "#123456" })

    private fun exportedHtml(payload: DeckWatchExportPayload = ReportFixtures.payload()): String =
        renderer.render(ReportDocument(payload = payload))

    @Test
    fun `an exported report round-trips through the importer`() {
        val original = ReportFixtures.payload()
        val parsed = PayloadParser.parse(exportedHtml(original))

        assertThat(parsed).isInstanceOf(PayloadParseResult.Parsed::class.java)
        val payload = (parsed as PayloadParseResult.Parsed).payload
        assertThat(payload.vessels).isEqualTo(original.vessels)
        assertThat(payload.decks).isEqualTo(original.decks)
        assertThat(payload.zones).isEqualTo(original.zones)
        assertThat(payload.equipment).isEqualTo(original.equipment)
        assertThat(payload.equipmentCategoryLinks).isEqualTo(original.equipmentCategoryLinks)
        assertThat(payload.taskInstances).isEqualTo(original.taskInstances)
        assertThat(payload.rounds).isEqualTo(original.rounds)
        assertThat(payload.roundItems).isEqualTo(original.roundItems)
        assertThat(payload.deficiencies).isEqualTo(original.deficiencies)
        assertThat(payload.userNotes).isEqualTo(original.userNotes)
        assertThat(payload).isEqualTo(original)
    }

    @Test
    fun `a raw JSON payload parses without the HTML wrapper`() {
        val parsed = PayloadParser.parse(ReportFixtures.payload().toJson())
        assertThat(parsed).isInstanceOf(PayloadParseResult.Parsed::class.java)
    }

    @Test
    fun `soft-deleted equipment survives the round trip so the deletion can propagate`() {
        val deleted = ReportFixtures.extinguisher.copy(deletedAt = 1_700_000_000_000L)
        val original = ReportFixtures.payload(equipment = listOf(deleted))
        val parsed = PayloadParser.parse(exportedHtml(original)) as PayloadParseResult.Parsed
        assertThat(parsed.payload.equipment.single().deletedAt).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `a truncated file is a typed failure, not an exception`() {
        val html = exportedHtml()
        val cut = html.substring(0, html.indexOf("deckwatch-data") + 400)
        val parsed = PayloadParser.parse(cut)

        assertThat(parsed).isInstanceOf(PayloadParseResult.Failed::class.java)
        assertThat((parsed as PayloadParseResult.Failed).reason).isEqualTo(ImportFailure.TRUNCATED_FILE)
        assertThat(parsed.detail).contains("truncated")
    }

    @Test
    fun `a file cut before the data block reports no data block`() {
        val html = exportedHtml()
        val cut = html.substring(0, html.indexOf("<div id=\"report\">"))
        val parsed = PayloadParser.parse(cut) as PayloadParseResult.Failed
        assertThat(parsed.reason).isEqualTo(ImportFailure.NO_DATA_BLOCK)
    }

    @Test
    fun `an empty file is refused clearly`() {
        val parsed = PayloadParser.parse("   \n\t ") as PayloadParseResult.Failed
        assertThat(parsed.reason).isEqualTo(ImportFailure.EMPTY_FILE)
    }

    @Test
    fun `an unrelated HTML page is refused as not a DeckWatch export`() {
        val parsed = PayloadParser.parse("<html><body><h1>Noon report</h1></body></html>")
            as PayloadParseResult.Failed
        assertThat(parsed.reason).isEqualTo(ImportFailure.NO_DATA_BLOCK)
    }

    @Test
    fun `a schema version mismatch is refused gracefully and names both versions`() {
        val future = ReportFixtures.payload().toJson()
            .replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val parsed = PayloadParser.parse(future) as PayloadParseResult.Failed

        assertThat(parsed.reason).isEqualTo(ImportFailure.UNSUPPORTED_SCHEMA_VERSION)
        assertThat(parsed.foundSchemaVersion).isEqualTo(99)
        assertThat(parsed.detail).contains("v99")
        assertThat(parsed.detail).contains("v${DeckWatchExportPayload.CURRENT_SCHEMA_VERSION}")
    }

    @Test
    fun `garbage inside the data block is a malformed-JSON failure`() {
        val html = exportedHtml().replace(
            Regex("(?s)(type=\"application/json\">).*?(</script>)"),
            "$1{not json at all$2",
        )
        val parsed = PayloadParser.parse(html) as PayloadParseResult.Failed
        assertThat(parsed.reason).isEqualTo(ImportFailure.MALFORMED_JSON)
    }

    @Test
    fun `an unknown field from a newer build is ignored rather than fatal`() {
        val withExtra = ReportFixtures.payload().toJson()
            .replaceFirst("{", "{\"somethingFromTheFuture\":[1,2,3],")
        val parsed = PayloadParser.parse(withExtra)
        assertThat(parsed).isInstanceOf(PayloadParseResult.Parsed::class.java)
    }

    @Test
    fun `a payload missing whole sections parses onto the defaults`() {
        val minimal = """{"schemaVersion":1,"appVersion":"x","generatedAtMillis":1}"""
        val parsed = PayloadParser.parse(minimal) as PayloadParseResult.Parsed
        assertThat(parsed.payload.vessels).isEmpty()
        assertThat(parsed.payload.equipment).isEmpty()
        assertThat(parsed.payload.appVersion).isEqualTo("x")
    }
}
