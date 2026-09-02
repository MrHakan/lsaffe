package com.deckwatch.feature.report

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The default export filename of §13.6:
 * `DeckWatch_{VESSELNAME}_{REPORTTYPE}_{yyyyMMdd_HHmm}.html`.
 *
 * Pure, so the sanitisation rules are unit-testable — and they need to be. The name has to survive
 * a FAT32 USB stick, a Windows Downloads folder, an email attachment header and a WhatsApp
 * document send, which between them rule out `/ \ : * ? " < > |`, control characters, trailing
 * dots and spaces, and reserved DOS device names.
 */
object ReportFileNames {

    const val PREFIX: String = "DeckWatch"
    const val HTML_EXTENSION: String = ".html"
    const val JSON_EXTENSION: String = ".json"
    const val CSV_EXTENSION: String = ".csv"

    /** Longest vessel-name fragment kept, so the whole name stays comfortably under 255 bytes. */
    const val MAX_VESSEL_CHARS: Int = 40

    /** Used when a vessel has no usable name at all. */
    const val FALLBACK_VESSEL: String = "VESSEL"

    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    /** Reserved DOS device names; still rejected by Windows in 2026, extension or not. */
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    /** The §13.6 filename for one report. */
    fun forReport(
        vesselName: String?,
        scope: ExportScope,
        atMillis: Long,
        extension: String = HTML_EXTENSION,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        append(PREFIX).append('_')
        append(sanitiseVesselName(vesselName)).append('_')
        append(scope.fileSlug).append('_')
        append(TIMESTAMP.format(Instant.ofEpochMilli(atMillis).atZone(zone)))
        append(extension)
    }

    /**
     * Reduce a vessel name to something every filesystem accepts.
     *
     * Letters and digits survive; everything else — spaces, punctuation, the `/` in "M/V", accented
     * characters a FAT32 stick may not round-trip — collapses to a single underscore. The result is
     * upper-cased because §13.6 writes `{VESSELNAME}` that way and because a register of files from
     * several ships sorts better in caps.
     */
    fun sanitiseVesselName(name: String?): String {
        val ascii = name.orEmpty().map { char ->
            when {
                char.isLetterOrDigit() && char.code < ASCII_LIMIT -> char
                // Turkish ships have Turkish names; transliterate rather than blank them out.
                else -> TRANSLITERATION[char.lowercaseChar()] ?: '_'
            }
        }.joinToString("")
        val collapsed = ascii.split('_')
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .take(MAX_VESSEL_CHARS)
            .trim('_')
            .uppercase()
        return when {
            collapsed.isEmpty() -> FALLBACK_VESSEL
            collapsed in RESERVED -> collapsed + "_"
            else -> collapsed
        }
    }

    /** Turkish (and a few common European) letters folded to their ASCII base. */
    private val TRANSLITERATION: Map<Char, Char> = mapOf(
        'ç' to 'C', 'ğ' to 'G', 'ı' to 'I', 'i' to 'I', 'ö' to 'O', 'ş' to 'S', 'ü' to 'U',
        'â' to 'A', 'î' to 'I', 'û' to 'U', 'é' to 'E', 'è' to 'E', 'á' to 'A', 'ñ' to 'N',
        'å' to 'A', 'ø' to 'O', 'æ' to 'A', 'ä' to 'A',
    )

    private const val ASCII_LIMIT = 128
}
