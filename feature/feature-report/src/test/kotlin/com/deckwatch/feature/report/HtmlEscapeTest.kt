package com.deckwatch.feature.report

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Escaping — the one thing between an officer's remark and a broken (or hostile) report. */
class HtmlEscapeTest {

    @Test
    fun `the five markup characters are escaped, including both quote styles`() {
        assertThat(HtmlEscape.escapeHtml("""<a href="x">Tom & Jerry's</a>"""))
            .isEqualTo("&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;")
    }

    @Test
    fun `an ampersand is escaped once, not twice`() {
        assertThat(HtmlEscape.escapeHtml("&amp;")).isEqualTo("&amp;amp;")
    }

    @Test
    fun `ordinary text passes through untouched`() {
        assertThat(HtmlEscape.escapeHtml("FE-UD-07 · Güverte · 12 kg")).isEqualTo("FE-UD-07 · Güverte · 12 kg")
    }

    @Test
    fun `a null or blank value renders as an em dash rather than the word null`() {
        assertThat(null.escOrDash()).isEqualTo("&mdash;")
        assertThat("   ".escOrDash()).isEqualTo("&mdash;")
        assertThat("LB No.1".escOrDash()).isEqualTo("LB No.1")
    }

    @Test
    fun `no angle bracket survives into the data block`() {
        val escaped = HtmlEscape.escapeJsonForScriptBlock("""{"a":"</script><!--<script>"}""")
        assertThat(escaped).doesNotContain("<")
        // A raw string, so the backslash-u sequences below are the six literal characters that
        // travel in the file — not a compiler escape.
        assertThat(escaped).isEqualTo("""{"a":"\u003c/script>\u003c!--\u003cscript>"}""")
    }

    @Test
    fun `the escaped block is still valid JSON and decodes to the original text`() {
        val payload = ReportFixtures.payload(notes = listOf(ReportFixtures.note.copy(body = "a < b </script>")))
        val block = HtmlEscape.escapeJsonForScriptBlock(payload.toJson())
        val parsed = PayloadParser.parseJson(block) as PayloadParseResult.Parsed
        assertThat(parsed.payload.userNotes.single().body).isEqualTo("a < b </script>")
    }

    @Test
    fun `the older backslash-slash form is still readable`() {
        assertThat(HtmlEscape.unescapeJsonFromScriptBlock("""{"a":"<\/script>"}"""))
            .isEqualTo("""{"a":"</script>"}""")
    }
}
