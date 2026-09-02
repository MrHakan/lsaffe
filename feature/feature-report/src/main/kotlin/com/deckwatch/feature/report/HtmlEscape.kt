package com.deckwatch.feature.report

/**
 * Escaping helpers for the renderer. Everything user-typed — a tag, a remark, a note body, a
 * vessel name — goes through [escapeHtml] before it reaches the document. There is no "trusted"
 * text in an export: the officer's own remark is as capable of containing `<` as anyone's.
 */
internal object HtmlEscape {

    /** Text-node and attribute-safe escaping. Quotes are escaped too, so this is safe in `"…"`. */
    fun escapeHtml(value: String): String {
        val out = StringBuilder(value.length + ESCAPE_HEADROOM)
        for (char in value) {
            when (char) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(char)
            }
        }
        return out.toString()
    }

    /**
     * Escapes a JSON document for embedding in `<script type="application/json">`.
     *
     * The HTML parser ends a script element at the first `</` that starts a valid end tag, wherever
     * it appears — including inside a JSON string. A remark reading `see </script> below` would
     * otherwise truncate the data block and, worse, spill the rest of the payload into the page as
     * markup. `<!--` and a nested `<script` are the other two sequences the legacy script grammar
     * reacts to, and between them they can swallow the rest of the block.
     *
     * Rather than special-case three sequences, **every `<` is written as its `\u003c` escape**. In
     * JSON a `<` can only occur inside a string, and `\u003c` is that same character written the
     * long way, so the rewrite is lossless and needs no matching un-escape: `JSON.parse` in the
     * browser and kotlinx.serialization on the way back in both read it as `<` unaided. The cost is
     * five bytes per angle bracket in user text, which is nothing, and the result is that no
     * tag-like sequence can exist inside the data block at all.
     */
    fun escapeJsonForScriptBlock(json: String): String = json.replace("<", "\\u003c")

    /**
     * Prepares a data block read out of a file for parsing — §13.5.
     *
     * A block written by [escapeJsonForScriptBlock] needs nothing done to it: it is already valid
     * JSON. This exists for the `<\/` form an older build or a hand-edited file may carry — also
     * valid JSON, but normalised here so there is one obvious place to handle any future wrapping.
     */
    fun unescapeJsonFromScriptBlock(text: String): String = text.replace("<\\/", "</")

    private const val ESCAPE_HEADROOM = 16
}

/** Shorthand used throughout the renderer. */
internal fun String.esc(): String = HtmlEscape.escapeHtml(this)

/** Escapes a nullable value, substituting an em dash for null/blank so tables never show "null". */
internal fun String?.escOrDash(): String =
    if (this.isNullOrBlank()) "&mdash;" else HtmlEscape.escapeHtml(this)
