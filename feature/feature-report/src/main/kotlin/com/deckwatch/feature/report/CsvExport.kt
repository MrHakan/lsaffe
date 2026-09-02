package com.deckwatch.feature.report

import com.deckwatch.core.common.Dates
import com.deckwatch.feature.inspection.DueExportRequest

/**
 * CSV export of the equipment register and the Due list — §13.5's last line.
 *
 * RFC 4180 to the letter, because the destination is a spreadsheet in a superintendent's office,
 * not a text editor: CRLF record separators, a field quoted whenever it contains a comma, a double
 * quote, CR or LF, and an embedded quote doubled. A UTF-8 BOM is written by default so Excel on
 * Windows opens a Turkish register without mangling every ğ and ş — an officer's register is full
 * of them.
 */
object CsvExport {

    /** RFC 4180 record separator. */
    const val CRLF: String = "\r\n"

    /** Excel needs this to recognise UTF-8 in a .csv. Harmless everywhere else. */
    const val UTF8_BOM: String = "﻿"

    /**
     * Quote a single field per RFC 4180 §2.6–2.7.
     *
     * A leading or trailing space is preserved by quoting too — a tag typed as `"FE-UD-07 "` is
     * data, and silently trimming it in an export would hide a real data-entry problem.
     */
    fun field(value: String?): String {
        val text = value.orEmpty()
        val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            text.startsWith(' ') || text.endsWith(' ')
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    /** One CSV record from [values]. */
    fun row(values: List<String?>): String = values.joinToString(",") { field(it) }

    /** The whole document: optional BOM, header row, then one record per row, CRLF-separated. */
    fun document(header: List<String>, rows: List<List<String?>>, withBom: Boolean = true): String {
        val body = (listOf(row(header)) + rows.map(::row)).joinToString(CRLF)
        return (if (withBom) UTF8_BOM else "") + body + CRLF
    }

    /** The equipment register — §13.5. Soft-deleted rows are excluded; a register lists what is aboard. */
    fun equipmentRegister(
        payload: DeckWatchExportPayload,
        labels: ReportLabels = ReportLabels(),
        typeNames: Map<String, String> = emptyMap(),
    ): String {
        val decks = payload.decks.associateBy { it.id }
        val zones = payload.zones.associateBy { it.id }
        val header = listOf(
            labels.colTag, labels.colName, labels.colType, labels.colDeck, labels.colZone,
            labels.colLocation, labels.colMaker, labels.colModel, labels.colSerial,
            labels.colApproval, labels.colQuantity, labels.colCondition, labels.colStatus,
            labels.colNextDue,
        )
        val rows = payload.equipment
            .filter { it.deletedAt == null }
            .sortedBy { it.tag }
            .map { item ->
                listOf(
                    item.tag,
                    item.name,
                    typeNames[item.typeKey] ?: item.typeKey,
                    item.deckId?.let { decks[it]?.name },
                    item.zoneId?.let { zones[it]?.name },
                    item.location,
                    item.makerName,
                    item.modelName,
                    item.serialNumber,
                    item.typeApprovalNumber,
                    item.quantity.toString(),
                    labels.condition(item.condition),
                    item.statusFlag.name,
                    item.nextDueDate?.let(Dates::formatIso),
                )
            }
        return document(header, rows)
    }

    /** The Due list exactly as the Due tab shows it — §12. */
    fun dueList(request: DueExportRequest, labels: ReportLabels = ReportLabels()): String {
        val header = listOf(
            labels.colTag, labels.colDeck, labels.colTask, labels.colDue,
            labels.colDays, labels.colBy, labels.colStatus,
        )
        val rows = request.lines.map { line ->
            listOf(
                line.tag,
                line.deck,
                line.task,
                Dates.formatIso(line.dueDate),
                if (line.dayDelta > 0) "+${line.dayDelta}" else line.dayDelta.toString(),
                labels.performer(line.performedBy),
                labels.taskStatus(line.status),
            )
        }
        return document(header, rows)
    }
}
