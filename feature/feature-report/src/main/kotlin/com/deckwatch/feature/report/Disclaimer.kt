package com.deckwatch.feature.report

/**
 * The §17.6 disclaimer, **verbatim**, as one paragraph of plain text.
 *
 * It is a constant rather than a string resource on purpose: an exported report is read on someone
 * else's phone, in a port state control office, months later. The wording must not vary with the
 * exporting device's locale, and it must not be possible for a translation to soften it. The
 * renderer puts it in the footer of every exported HTML report — §13.4, §17.6.
 *
 * Do not reword, abbreviate or reflow this text.
 */
const val REPORT_DISCLAIMER: String =
    "DeckWatch is a planning and record-keeping aid. It is not a certificate, not a substitute " +
        "for the vessel's approved plans, the manufacturer's manuals, class rules or the flag " +
        "Administration's instructions, and it does not discharge any statutory obligation. " +
        "Regulatory content is a summary captured on a stated date and may be superseded. Always " +
        "verify against the current instrument and the vessel's own documentation. The Master's " +
        "and the Company's responsibilities under SOLAS and the ISM Code are unaffected."

/**
 * A distinctive fragment of [REPORT_DISCLAIMER] that a test — or a reader checking a file that has
 * been through a mail gateway — can search for without depending on the whole paragraph.
 */
const val REPORT_DISCLAIMER_ANCHOR: String = "It is not a certificate"
