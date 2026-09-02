package com.deckwatch.feature.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Why an import could not even be read. The UI maps these to localised sentences. */
enum class ImportFailure {
    /** Nothing in the file, or nothing but whitespace. */
    EMPTY_FILE,

    /** An HTML file with no `deckwatch-data` block — not a DeckWatch export. */
    NO_DATA_BLOCK,

    /** The block starts but never ends: the file was cut short in transit — §17.4. */
    TRUNCATED_FILE,

    /** The block is there and complete, but is not valid JSON. */
    MALFORMED_JSON,

    /** Valid JSON, valid structure, but a `schemaVersion` this build does not read — §13.5. */
    UNSUPPORTED_SCHEMA_VERSION,

    /** The file could not be opened or decoded as text at all. */
    UNREADABLE,
}

/** The outcome of reading a candidate import file. Never an exception — §17.4. */
sealed interface PayloadParseResult {
    data class Parsed(val payload: DeckWatchExportPayload) : PayloadParseResult

    /**
     * @param detail an English sentence naming what was wrong, safe to log and to show under a
     *   localised heading. Includes the offending schema version when that is the problem.
     */
    data class Failed(
        val reason: ImportFailure,
        val detail: String,
        val foundSchemaVersion: Int? = null,
    ) : PayloadParseResult
}

/**
 * Reads a `deckwatch-data` block back out of an exported `.html`, or a bare `.json` payload —
 * §13.5.
 *
 * Written defensively on purpose. The files this sees have been through WhatsApp, a ship's mail
 * gateway, a USB stick and someone's Downloads folder; being handed half a file is normal, not
 * exceptional. Every path returns a [PayloadParseResult]; nothing throws.
 */
object PayloadParser {

    /** The id of the data block, as written by [HtmlReportRenderer]. */
    const val DATA_BLOCK_ID: String = "deckwatch-data"

    /** Parse [text] as either an exported HTML report or a raw JSON payload. */
    fun parse(text: String): PayloadParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return PayloadParseResult.Failed(ImportFailure.EMPTY_FILE, "The file is empty.")
        }
        val json = if (trimmed.startsWith("{")) {
            trimmed
        } else {
            when (val extracted = extractDataBlock(text)) {
                is BlockResult.Found -> extracted.json
                is BlockResult.Missing -> return PayloadParseResult.Failed(
                    ImportFailure.NO_DATA_BLOCK,
                    "This file has no DeckWatch data block, so there is nothing to import from it.",
                )

                is BlockResult.Truncated -> return PayloadParseResult.Failed(
                    ImportFailure.TRUNCATED_FILE,
                    "The DeckWatch data block starts but never ends — the file is truncated. " +
                        "Ask for it to be sent again.",
                )
            }
        }
        return parseJson(json)
    }

    /** Parse a JSON payload that has already been separated from any HTML wrapper. */
    fun parseJson(json: String): PayloadParseResult {
        val version = runCatching {
            val root = PayloadJson.parseToJsonElement(json)
            (root as? JsonObject)?.get("schemaVersion")
                ?.let { (it as? JsonPrimitive)?.intOrNull }
        }.getOrElse {
            return PayloadParseResult.Failed(
                ImportFailure.MALFORMED_JSON,
                "The data block is not valid JSON: ${it.message.orEmpty().take(MAX_DETAIL)}",
            )
        }

        if (version != null && version != DeckWatchExportPayload.CURRENT_SCHEMA_VERSION) {
            return PayloadParseResult.Failed(
                ImportFailure.UNSUPPORTED_SCHEMA_VERSION,
                "This file was written with data schema v$version; this build of DeckWatch reads " +
                    "v${DeckWatchExportPayload.CURRENT_SCHEMA_VERSION}. Update the app on the " +
                    "device that reads the file, or re-export from a matching version.",
                foundSchemaVersion = version,
            )
        }

        return runCatching {
            PayloadParseResult.Parsed(PayloadJson.decodeFromString<DeckWatchExportPayload>(json))
        }.getOrElse {
            PayloadParseResult.Failed(
                ImportFailure.MALFORMED_JSON,
                "The data block could not be read: ${it.message.orEmpty().take(MAX_DETAIL)}",
            )
        }
    }

    /** Outcome of locating the `<script id="deckwatch-data">` block. */
    private sealed interface BlockResult {
        data class Found(val json: String) : BlockResult
        data object Missing : BlockResult
        data object Truncated : BlockResult
    }

    /**
     * Locate and un-escape the data block.
     *
     * Deliberately not a regular expression and deliberately not an XML parse: the input may be
     * malformed HTML, and a backtracking regex over a multi-megabyte file is its own denial of
     * service. Plain index arithmetic over the string is predictable in both time and failure mode.
     */
    private fun extractDataBlock(html: String): BlockResult {
        val idAt = html.indexOf(DATA_BLOCK_ID, ignoreCase = true)
        if (idAt < 0) return BlockResult.Missing
        val tagStart = html.lastIndexOf("<script", startIndex = idAt, ignoreCase = true)
        if (tagStart < 0) return BlockResult.Missing
        val contentStart = html.indexOf('>', startIndex = idAt)
        if (contentStart < 0) return BlockResult.Truncated
        val contentEnd = html.indexOf("</script", startIndex = contentStart, ignoreCase = true)
        if (contentEnd < 0) return BlockResult.Truncated
        val body = html.substring(contentStart + 1, contentEnd).trim()
        if (body.isEmpty()) return BlockResult.Truncated
        return BlockResult.Found(HtmlEscape.unescapeJsonFromScriptBlock(body))
    }

    private const val MAX_DETAIL = 200
}
